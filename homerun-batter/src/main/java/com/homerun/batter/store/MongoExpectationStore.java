package com.homerun.batter.store;

import com.homerun.batter.audit.MockInvocationAudit;
import com.homerun.batter.exception.MockExpectationExpiredException;
import com.homerun.batter.exception.MockExpectationNotFoundException;
import com.homerun.common.model.MockExpectation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB-backed implementation of {@link ExpectationStore}.
 */
@Slf4j
@RequiredArgsConstructor
public class MongoExpectationStore implements ExpectationStore {

    private final ExpectationMongoRepository repository;
    private final MockInvocationAudit        audit;

    @Override
    public MockExpectation fetch(UUID scenarioId, String targetService, String operationName) {
        MockExpectation expectation = repository
                .findByScenarioIdAndTargetServiceAndOperationName(
                        scenarioId, targetService, operationName)
                .orElseThrow(() -> new MockExpectationNotFoundException(
                        scenarioId, targetService, operationName));

        validateExpiry(expectation);
        audit.record(expectation);
        repository.incrementInvocationCount(expectation.getId(), Instant.now());

        log.debug("Serving mock expectation [scenario={}, service={}, op={}, invocations={}]",
                scenarioId, targetService, operationName,
                expectation.getInvocationCount() + 1);

        return expectation;
    }

    private void validateExpiry(MockExpectation expectation) {
        if (expectation.getExpiresAt() != null
                && expectation.getExpiresAt().isBefore(Instant.now())) {
            throw new MockExpectationExpiredException(
                    expectation.getScenarioId(),
                    expectation.getTargetService(),
                    expectation.getOperationName());
        }
    }
}
