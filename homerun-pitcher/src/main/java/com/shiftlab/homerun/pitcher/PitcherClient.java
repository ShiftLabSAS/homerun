package com.shiftlab.homerun.pitcher;

import org.springframework.http.HttpHeaders;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftlab.homerun.common.constants.MockHeaders;
import com.shiftlab.homerun.common.model.MockBehaviorType;
import com.shiftlab.homerun.common.model.MockExpectation;
import com.shiftlab.homerun.pitcher.autoconfigure.PitcherProperties;
import com.shiftlab.homerun.pitcher.repository.PitcherExpectationRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Primary API for test code to submit expectations and drive mock-mode HTTP
 * calls.
 *
 * <h3>Typical per-test usage</h3>
 *
 * <pre>
 * {@code
 * &#64;BeforeEach
 * void setUp() {
 *     pitcher.open();
 * }
 *
 * &#64;AfterEach
 * void tearDown() {
 *     pitcher.close();
 * }
 *
 * @Test
 * void myTest() {
 *     pitcher.expect("payments", "charge", chargeReq, chargeResp);
 *
 *     ResponseEntity<Result> response = pitcher.get("/api/order", Result.class);
 *     assertThat(response.getBody()).isNotNull();
 * }
 * }
 * </pre>
 *
 * <p>
 * {@link #open()} creates a fresh scenario UUID. All {@link #expect} calls and
 * HTTP helpers ({@link #get}) use that UUID automatically — no manual header
 * wiring
 * needed. {@link #close()} deletes every expectation from the session.
 *
 * <h3>Advanced: independent scenario handles</h3>
 *
 * <pre>{@code
 * try (ScenarioHandle scenario = pitcher.newScenario()) {
 *     scenario
 *             .expect("payments", "charge", chargeReq, chargeResp)
 *             .expect("notifications", "send", notifReq, notifResp);
 *     // ... make calls passing scenario.getScenarioId() in headers manually
 * } // auto-cleanup
 * }</pre>
 */
@Slf4j
public class PitcherClient {

    private final PitcherExpectationRepository repository;
    private final ObjectMapper objectMapper;
    private final PitcherProperties properties;
    /**
     * Null when no {@code TestRestTemplate} is available in the application
     * context.
     */
    private final RestTemplate restTemplate;

    /**
     * Scenario UUID for the current open/close cycle; null when no session is
     * active.
     */
    private UUID activeScenarioId;

    public PitcherClient(PitcherExpectationRepository repository,
            ObjectMapper objectMapper,
            PitcherProperties properties,
            RestTemplate restTemplate) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts a new mock session with a fresh scenario UUID.
     * Call this in {@code @BeforeEach}.
     */
    public PitcherClient open() {
        activeScenarioId = UUID.randomUUID();
        log.debug("Pitcher session opened [scenario={}]", activeScenarioId);
        return this;
    }

    /**
     * Deletes all expectations registered since the last {@link #open()} call.
     * Call this in {@code @AfterEach}.
     */
    public void close() {
        if (activeScenarioId != null) {
            cleanup(activeScenarioId);
            activeScenarioId = null;
        }
    }

    // ── Fluent expectation registration ──────────────────────────────────────

    /**
     * Registers a success expectation on the active scenario.
     * Requires a prior call to {@link #open()}.
     *
     * @param targetService logical service key, e.g. {@code "payments"}
     * @param operationName method name on the client interface, e.g.
     *                      {@code "charge"}
     * @param request       the request the backend will send (stored for audit)
     * @param response      the response to return when the mock is invoked
     */
    public PitcherClient expect(String targetService,
            String operationName,
            Object request,
            Object response) {
        requireOpenSession();
        pitch(PitchRequest.builder()
                .scenarioId(activeScenarioId)
                .targetService(targetService)
                .operationName(operationName)
                .request(request)
                .response(response)
                .behaviorType(MockBehaviorType.SUCCESS)
                .build());
        return this;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    /**
     * Issues a GET to the application under test with mock headers automatically
     * injected from the active scenario. Requires a prior call to {@link #open()}.
     *
     * @param path         relative path, e.g. {@code "/posts/1"}
     * @param responseType expected response body type
     */
    public <T> ResponseEntity<T> get(String path, Class<T> responseType) {
        requireOpenSession();
        requireRestTemplate();
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(buildMockHeaders()), responseType);
    }

    // ── Scenario factory (advanced) ───────────────────────────────────────────

    /** Creates an independent scenario handle with a random UUID. */
    public ScenarioHandle newScenario() {
        return new ScenarioHandle(UUID.randomUUID(), this);
    }

    /** Creates an independent scenario handle with the supplied UUID. */
    public ScenarioHandle newScenario(UUID scenarioId) {
        return new ScenarioHandle(scenarioId, this);
    }

    // ── Single expectation submission (low-level) ─────────────────────────────

    /**
     * Serializes both the request and response on the {@link PitchRequest} and
     * persists the expectation document.
     *
     * @throws PitchException if JSON serialization fails
     */
    public MockExpectation pitch(PitchRequest request) {
        validate(request);

        MockExpectation doc = MockExpectation.builder()
                .scenarioId(request.getScenarioId())
                .targetService(request.getTargetService())
                .operationName(request.getOperationName())
                .serializedRequest(serialize(request.getRequest()))
                .serializedResponse(serialize(request.getResponse()))
                .behaviorType(request.getBehaviorType())
                .errorClassName(request.getErrorClassName())
                .errorMessage(request.getErrorMessage())
                .httpStatus(request.getHttpStatus())
                .delayMillis(request.getTimeout() != null
                        ? request.getTimeout().toMillis()
                        : null)
                .createdBy(resolveCreatedBy(request))
                .createdAt(Instant.now())
                .expiresAt(resolveExpiresAt(request))
                .description(request.getDescription())
                .testSuiteName(request.getTestSuiteName())
                .build();

        MockExpectation saved = repository.save(doc);
        log.debug("Pitched expectation [scenario={} service={} op={}]",
                saved.getScenarioId(), saved.getTargetService(), saved.getOperationName());
        return saved;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Deletes all expectations for the given scenario. */
    public void cleanup(UUID scenarioId) {
        repository.deleteAllByScenarioId(scenarioId);
        log.debug("Cleaned up expectations for scenario={}", scenarioId);
    }

    /** Deletes all expectations tagged with the given test suite name. */
    public void cleanupSuite(String testSuiteName) {
        repository.deleteAllByTestSuiteName(testSuiteName);
        log.debug("Cleaned up expectations for testSuite={}", testSuiteName);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private HttpHeaders buildMockHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(MockHeaders.MOCK_MODE, "true");
        headers.set(MockHeaders.MOCK_SCENARIO_ID, activeScenarioId.toString());
        headers.set(MockHeaders.MOCK_AUTH_TOKEN, properties.getAuthToken());
        return headers;
    }

    private void requireOpenSession() {
        if (activeScenarioId == null) {
            throw new IllegalStateException(
                    "No active pitcher session — call pitcher.open() before using this method");
        }
    }

    private void requireRestTemplate() {
        if (restTemplate == null) {
            throw new IllegalStateException(
                    "No RestTemplate available — ensure TestRestTemplate is present in the "
                            + "application context (e.g. @SpringBootTest with WebEnvironment.RANDOM_PORT)");
        }
    }

    private void validate(PitchRequest request) {
        if (request.getScenarioId() == null) {
            throw new PitchException("scenarioId must not be null");
        }
        if (request.getTargetService() == null || request.getTargetService().isBlank()) {
            throw new PitchException("targetService must not be blank");
        }
        if (request.getOperationName() == null || request.getOperationName().isBlank()) {
            throw new PitchException("operationName must not be blank");
        }
    }

    private String serialize(Object value) {
        if (value == null)
            return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new PitchException("Failed to serialize object of type "
                    + value.getClass().getSimpleName(), e);
        }
    }

    private String resolveCreatedBy(PitchRequest request) {
        if (request.getCreatedBy() != null && !request.getCreatedBy().isBlank()) {
            return request.getCreatedBy();
        }
        return properties.getDefaultCreatedBy();
    }

    private Instant resolveExpiresAt(PitchRequest request) {
        if (request.getExpiresAt() != null) {
            return request.getExpiresAt();
        }
        if (request.getTtl() != null) {
            return Instant.now().plus(request.getTtl());
        }
        return Instant.now().plus(properties.getDefaultTtl());
    }
}
