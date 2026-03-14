package com.shiftlab.homerun.batter.audit;

import com.shiftlab.homerun.common.model.MockExpectation;
import lombok.extern.slf4j.Slf4j;

/**
 * Records each mock invocation for observability. The default implementation
 * writes a structured log line. Replace or extend to push entries to an audit
 * table, metrics counter, or tracing span.
 */
@Slf4j
public class MockInvocationAudit {

    public void record(MockExpectation expectation) {
        log.info("MOCK_INVOCATION scenario={} service={} op={} behavior={} createdBy={}",
                expectation.getScenarioId(),
                expectation.getTargetService(),
                expectation.getOperationName(),
                expectation.getBehaviorType(),
                expectation.getCreatedBy());
    }
}
