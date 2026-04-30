package ar.shiftlab.homerun.batter.store;

import ar.shiftlab.homerun.common.model.MockExpectation;

import java.util.UUID;

/**
 * Port for fetching expectations. The default implementation reads from MongoDB;
 * alternative stores (Redis, in-memory) can be plugged in by providing a bean
 * that implements this interface.
 */
public interface ExpectationStore {

    /**
     * Fetches the expectation for the given coordinates, increments its invocation
     * counter, and validates its expiry.
     *
     * @throws ar.shiftlab.homerun.batter.exception.MockExpectationNotFoundException if no
     *         matching expectation exists
     * @throws ar.shiftlab.homerun.batter.exception.MockExpectationExpiredException if the
     *         expectation has passed its {@code expiresAt} timestamp
     */
    MockExpectation fetch(UUID scenarioId, String targetService, String operationName);
}
