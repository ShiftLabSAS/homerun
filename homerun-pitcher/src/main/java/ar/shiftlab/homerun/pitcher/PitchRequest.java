package ar.shiftlab.homerun.pitcher;

import ar.shiftlab.homerun.common.model.MockBehaviorType;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Describes a single expectation to be submitted to the mock store.
 *
 * <p>Build via {@link PitcherClient#newScenario()} for a fluent multi-expectation
 * setup, or directly via the static builder for one-off submissions.
 *
 * <pre>{@code
 * PitchRequest.builder()
 *     .scenarioId(scenarioId)
 *     .targetService("payments")
 *     .operationName("charge")
 *     .request(chargeRequest)
 *     .response(chargeResponse)
 *     .ttl(Duration.ofHours(1))
 *     .build();
 * }</pre>
 */
@Value
@Builder
public class PitchRequest {

    /** UUID that groups all expectations belonging to one test execution. */
    UUID scenarioId;

    /** Logical key for the downstream service, e.g. {@code "payments"}. */
    String targetService;

    /** Method/operation name on the client interface, e.g. {@code "charge"}. */
    String operationName;

    /**
     * The request object the backend will send to the downstream service.
     * Serialized to JSON and stored for audit / optional strict matching.
     * May be {@code null} for error scenarios where the request is irrelevant.
     */
    Object request;

    /**
     * The response object the downstream service should return.
     * Serialized to JSON and deserialized back at call time by the batter.
     * Required when {@code behaviorType} is {@link MockBehaviorType#SUCCESS}.
     */
    Object response;

    /** Defaults to {@link MockBehaviorType#SUCCESS}. */
    @Builder.Default
    MockBehaviorType behaviorType = MockBehaviorType.SUCCESS;

    // ── Error-scenario fields ─────────────────────────────────────────────────

    /**
     * Fully-qualified class name of the exception to throw.
     * Required when {@code behaviorType} is {@link MockBehaviorType#BUSINESS_ERROR}.
     */
    String errorClassName;
    String errorMessage;

    /** HTTP status code for {@link MockBehaviorType#HTTP_ERROR} scenarios. */
    Integer httpStatus;

    /** Delay before throwing for {@link MockBehaviorType#TIMEOUT} scenarios. */
    Duration timeout;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * How long the expectation stays valid.
     * Defaults to {@link PitcherProperties#getDefaultTtl()} when not set.
     */
    Duration ttl;

    /** Hard expiry; takes precedence over {@code ttl} when both are set. */
    Instant expiresAt;

    String description;
    String testSuiteName;
    String createdBy;
}
