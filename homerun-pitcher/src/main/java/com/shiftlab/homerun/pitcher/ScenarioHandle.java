package com.shiftlab.homerun.pitcher;

import com.shiftlab.homerun.common.model.MockBehaviorType;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Fluent handle for building and later cleaning up all expectations belonging
 * to a single test scenario.
 *
 * <p>Implements {@link AutoCloseable} so tests can use try-with-resources for
 * guaranteed cleanup even when assertions fail:
 *
 * <pre>{@code
 * try (ScenarioHandle scenario = pitcherClient.newScenario()) {
 *     scenario
 *         .expect("payments", "charge", chargeReq, chargeResp)
 *         .expect("notifications", "send",  notifReq,  notifResp);
 *
 *     // execute the real API call
 *     graphqlClient.mutate(PLACE_ORDER_MUTATION, vars);
 *
 *     // assertions ...
 * } // expectations deleted automatically
 * }</pre>
 */
@RequiredArgsConstructor
public class ScenarioHandle implements AutoCloseable {

    private final UUID         scenarioId;
    private final PitcherClient client;

    public UUID getScenarioId() {
        return scenarioId;
    }

    // ── Fluent expectation registration ──────────────────────────────────────

    /**
     * Records a success expectation for the given service and operation.
     *
     * @param targetService  logical service key, e.g. {@code "payments"}
     * @param operationName  method name on the client interface, e.g. {@code "charge"}
     * @param request        the request the backend will send (serialized for audit)
     * @param response       the response to return (serialized and deserialized by batter)
     */
    public ScenarioHandle expect(String targetService,
                                 String operationName,
                                 Object request,
                                 Object response) {
        client.pitch(PitchRequest.builder()
                .scenarioId(scenarioId)
                .targetService(targetService)
                .operationName(operationName)
                .request(request)
                .response(response)
                .behaviorType(MockBehaviorType.SUCCESS)
                .build());
        return this;
    }

    /**
     * Records a full {@link PitchRequest}, including error scenarios, TTL overrides,
     * and metadata. The {@code scenarioId} on the request is overwritten with this
     * handle's scenario ID to keep the group consistent.
     */
    public ScenarioHandle expect(PitchRequest request) {
        // enforce the handle's own scenarioId
        client.pitch(PitchRequest.builder()
                .scenarioId(scenarioId)
                .targetService(request.getTargetService())
                .operationName(request.getOperationName())
                .request(request.getRequest())
                .response(request.getResponse())
                .behaviorType(request.getBehaviorType())
                .errorClassName(request.getErrorClassName())
                .errorMessage(request.getErrorMessage())
                .httpStatus(request.getHttpStatus())
                .timeout(request.getTimeout())
                .ttl(request.getTtl())
                .expiresAt(request.getExpiresAt())
                .description(request.getDescription())
                .testSuiteName(request.getTestSuiteName())
                .createdBy(request.getCreatedBy())
                .build());
        return this;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Deletes all expectations for this scenario from the mock store.
     * Safe to call multiple times.
     */
    public void cleanup() {
        client.cleanup(scenarioId);
    }

    @Override
    public void close() {
        cleanup();
    }
}
