package com.shiftlab.homerun.pitcher.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.shiftlab.homerun.common.model.MockExpectation;

/**
 * Spring Data repository used by the pitcher to write expectations.
 * The batter has its own repository in its own package; both map to the
 * same {@code mock_expectations} collection.
 */
public interface PitcherExpectationRepository extends MongoRepository<MockExpectation, String> {

    void deleteAllByScenarioId(UUID scenarioId);

    void deleteAllByTestSuiteName(String testSuiteName);

    long countByScenarioId(UUID scenarioId);
}
