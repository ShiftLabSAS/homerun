package com.homerun.batter.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homerun.batter.context.RequestMockContext;
import com.homerun.batter.context.RequestMockContextHolder;
import com.homerun.batter.exception.MockConfigurationException;
import com.homerun.batter.exception.MockDeserializationException;
import com.homerun.batter.exception.MockRequestMismatchException;
import com.homerun.batter.store.ExpectationStore;
import com.homerun.common.model.MockExpectation;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Abstract base class for every mock downstream client implementation.
 *
 * <p>Subclasses declare their service key and implement the same interface as the
 * real client. Each method body reduces to a single call to
 * {@link #executeWithExpectation(String, Object, Class)}:
 *
 * <pre>{@code
 * @Component
 * @ConditionalOnProperty("homerun.batter.enabled")
 * public class MockPaymentServiceClient
 *         extends MockClientSupport
 *         implements PaymentServiceClient {
 *
 *     public MockPaymentServiceClient(ExpectationStore store,
 *                                     RequestMockContext ctx,
 *                                     ObjectMapper mapper) {
 *         super(store, ctx, mapper);
 *     }
 *
 *     @Override protected String serviceKey() { return "payments"; }
 *
 *     @Override
 *     public PaymentResult charge(PaymentDetails details) {
 *         return executeWithExpectation("charge", details, PaymentResult.class);
 *     }
 * }
 * }</pre>
 *
 * <h3>Request/response lifecycle</h3>
 * <ol>
 *   <li>Resolve the active scenario UUID from the request-scoped context (or the
 *       thread-local snapshot for async calls).</li>
 *   <li>Fetch the matching {@link MockExpectation} from the store.</li>
 *   <li>Optionally verify that the actual request matches the recorded one
 *       (controlled by {@code homerun.batter.strict-request-matching}).</li>
 *   <li>Dispatch based on {@link com.homerun.common.model.MockBehaviorType}:
 *       deserialize the stored response JSON, throw the stored exception type,
 *       simulate a timeout, etc.</li>
 * </ol>
 */
@Slf4j
public abstract class MockClientSupport {

    private final ExpectationStore    store;
    private final RequestMockContext  requestMockContext;
    private final ObjectMapper        objectMapper;
    private final boolean             strictRequestMatching;

    protected MockClientSupport(ExpectationStore   store,
                                RequestMockContext requestMockContext,
                                ObjectMapper       objectMapper,
                                boolean            strictRequestMatching) {
        this.store                 = store;
        this.requestMockContext    = requestMockContext;
        this.objectMapper          = objectMapper;
        this.strictRequestMatching = strictRequestMatching;
    }

    /**
     * The logical key that identifies this service in the expectation store.
     * Must match the {@code targetService} value used when the expectation was pitched.
     */
    protected abstract String serviceKey();

    /**
     * The service interface this mock implements.
     *
     * <p>Used by {@link com.homerun.batter.HomeRunBatter} to auto-build its internal
     * registry so callers can write:
     * <pre>{@code
     * batter.isActive() ? batter.mockService(TwitterService.class) : new TwitterService()
     * }</pre>
     *
     * <p>Must match the interface that the paired real client also implements.
     */
    public abstract Class<?> targetInterface();

    /**
     * Looks up the expectation for the given operation, optionally verifies the
     * inbound request against the stored one, then dispatches the configured behavior.
     *
     * @param operationName the method name on the client interface (e.g. {@code "charge"})
     * @param actualRequest the request object the backend is about to send — used for
     *                      audit and optional strict matching; may be {@code null}
     * @param returnType    the concrete class to deserialize the stored response into
     * @return the deserialized response, or {@code null} for void operations
     */
    protected <T> T executeWithExpectation(String   operationName,
                                           Object   actualRequest,
                                           Class<T> returnType) {
        UUID scenarioId = resolveScenarioId();

        MockExpectation expectation = store.fetch(scenarioId, serviceKey(), operationName);

        if (strictRequestMatching) {
            verifyRequest(actualRequest, expectation, operationName);
        }

        return switch (expectation.getBehaviorType()) {
            case SUCCESS            -> deserializeResponse(expectation, returnType);
            case BUSINESS_ERROR     -> throwBusinessError(expectation);
            case HTTP_ERROR         -> throwHttpError(expectation);
            case TIMEOUT            -> simulateTimeout(expectation);
            case MALFORMED_RESPONSE -> throwMalformedResponse(operationName);
        };
    }

    // ── Scenario ID resolution ────────────────────────────────────────────────

    /**
     * Prefers the request-scoped bean (servlet thread). Falls back to the ThreadLocal
     * snapshot for async worker threads that have been decorated by
     * {@link com.homerun.batter.async.MockContextTaskDecorator}.
     */
    private UUID resolveScenarioId() {
        try {
            if (requestMockContext.isMockModeEnabled()) {
                return requestMockContext.getScenarioId();
            }
        } catch (Exception ignored) {
            // The request scope proxy throws when accessed outside a request context.
        }

        RequestMockContextHolder.Snapshot snapshot = RequestMockContextHolder.get();
        if (snapshot != null && snapshot.mockModeEnabled()) {
            return snapshot.scenarioId();
        }

        throw new IllegalStateException(
                "MockClientSupport invoked outside of an active mock context. "
                + "Ensure MockHeaderFilter has processed the request and "
                + "X-Mock-Mode: true was sent.");
    }

    // ── Behavior dispatch ─────────────────────────────────────────────────────

    private <T> T deserializeResponse(MockExpectation expectation, Class<T> returnType) {
        String json = expectation.getSerializedResponse();
        if (json == null || json.isBlank()) {
            return null;  // void / null response
        }
        try {
            return objectMapper.readValue(json, returnType);
        } catch (JsonProcessingException e) {
            throw new MockDeserializationException(
                    "Failed to deserialize mock response for [service=%s, op=%s, type=%s]"
                            .formatted(serviceKey(), expectation.getOperationName(),
                                    returnType.getSimpleName()), e);
        }
    }

    /**
     * Reflectively instantiates the exception class stored in the expectation so
     * the service layer's {@code catch} blocks handle exactly the same type they
     * would receive from the real downstream client.
     */
    @SuppressWarnings("unchecked")
    private <T> T throwBusinessError(MockExpectation expectation) {
        String className = expectation.getErrorClassName();
        String message   = expectation.getErrorMessage();
        try {
            Class<? extends RuntimeException> exClass =
                    (Class<? extends RuntimeException>) Class.forName(className);
            Constructor<? extends RuntimeException> ctor =
                    exClass.getConstructor(String.class);
            throw ctor.newInstance(message);
        } catch (ReflectiveOperationException e) {
            throw new MockConfigurationException(
                    "Cannot instantiate business error class: " + className, e);
        }
    }

    private <T> T throwHttpError(MockExpectation expectation) {
        throw new MockHttpErrorException(expectation.getHttpStatus(), expectation.getErrorMessage());
    }

    private <T> T simulateTimeout(MockExpectation expectation) {
        long delayMs = expectation.getDelayMillis() != null ? expectation.getDelayMillis() : 30_000L;
        log.debug("Simulating timeout of {}ms for [service={}, op={}]",
                delayMs, serviceKey(), expectation.getOperationName());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new MockTimeoutException(
                "Mock timeout after %dms for [service=%s, op=%s]"
                        .formatted(delayMs, serviceKey(), expectation.getOperationName()));
    }

    private <T> T throwMalformedResponse(String operationName) {
        throw new MockDeserializationException(
                "Mock malformed response for [service=%s, op=%s]"
                        .formatted(serviceKey(), operationName), null);
    }

    // ── Request verification ──────────────────────────────────────────────────

    private void verifyRequest(Object actual, MockExpectation expectation, String operationName) {
        String storedJson = expectation.getSerializedRequest();
        if (storedJson == null || actual == null) return;

        try {
            String actualJson = objectMapper.writeValueAsString(actual);
            // Normalize both through the mapper so field order doesn't matter.
            if (!objectMapper.readTree(actualJson).equals(objectMapper.readTree(storedJson))) {
                throw new MockRequestMismatchException(
                        "Request mismatch for [service=%s, op=%s]. Expected: %s  Actual: %s"
                                .formatted(serviceKey(), operationName, storedJson, actualJson));
            }
        } catch (JsonProcessingException e) {
            throw new MockDeserializationException("Failed to compare request payloads", e);
        }
    }

    // ── Nested exception types ────────────────────────────────────────────────

    public static class MockHttpErrorException extends RuntimeException {
        private final int httpStatus;
        public MockHttpErrorException(Integer status, String message) {
            super(message);
            this.httpStatus = status != null ? status : 500;
        }
        public int getHttpStatus() { return httpStatus; }
    }

    public static class MockTimeoutException extends RuntimeException {
        public MockTimeoutException(String message) { super(message); }
    }
}
