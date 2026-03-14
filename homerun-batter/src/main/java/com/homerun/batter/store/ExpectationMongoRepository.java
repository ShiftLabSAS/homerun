package com.homerun.batter.store;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import com.homerun.common.model.MockExpectation;

/**
 * Spring Data repository used by the batter to read and update expectations.
 */
public interface ExpectationMongoRepository extends MongoRepository<MockExpectation, String> {

    Optional<MockExpectation> findByScenarioIdAndTargetServiceAndOperationName(
            UUID scenarioId, String targetService, String operationName);

    /**
     * Atomic increment of the invocation counter — avoids a read-modify-write
     * race when parallel requests hit the same expectation.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'invocationCount': 1 }, '$set': { 'lastInvokedAt': ?1 } }")
    void incrementInvocationCount(String id, Instant lastInvokedAt);
}
