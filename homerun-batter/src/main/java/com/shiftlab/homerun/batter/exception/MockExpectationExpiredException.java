package com.shiftlab.homerun.batter.exception;

import java.util.UUID;

public class MockExpectationExpiredException extends RuntimeException {

    public MockExpectationExpiredException(UUID scenarioId, String targetService, String operationName) {
        super("Mock expectation has expired [scenario=%s, service=%s, op=%s]"
                .formatted(scenarioId, targetService, operationName));
    }
}
