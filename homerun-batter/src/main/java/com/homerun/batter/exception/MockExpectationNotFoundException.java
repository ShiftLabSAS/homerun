package com.homerun.batter.exception;

import java.util.UUID;

public class MockExpectationNotFoundException extends RuntimeException {

    public MockExpectationNotFoundException(UUID scenarioId, String targetService, String operationName) {
        super("No mock expectation found for [scenario=%s, service=%s, op=%s]"
                .formatted(scenarioId, targetService, operationName));
    }
}
