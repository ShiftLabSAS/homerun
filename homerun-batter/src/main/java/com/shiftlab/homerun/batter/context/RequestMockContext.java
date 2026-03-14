package com.shiftlab.homerun.batter.context;

import lombok.Getter;
import org.springframework.web.context.annotation.RequestScope;

import java.time.Instant;
import java.util.UUID;

/**
 * Request-scoped bean that carries mock-mode state for the duration of a single
 * HTTP request. Injected into the {@link com.shiftlab.homerun.batter.support.MockClientSupport}
 * base class via a Spring scoped proxy, so the singleton router never caches state
 * between requests.
 *
 * <p>Populated by {@link com.shiftlab.homerun.batter.filter.MockHeaderFilter} early in the
 * filter chain. Remains in its default (inactive) state for every non-mock request,
 * so normal production traffic is completely unaffected.
 */
@RequestScope
public class RequestMockContext {

    @Getter private boolean mockModeEnabled = false;
    @Getter private UUID    scenarioId;
    @Getter private String  activatedBy;
    @Getter private Instant activatedAt;

    /**
     * Activates mock mode for this request. Called exactly once per request by the
     * filter after all security validations have passed.
     */
    public void activate(UUID scenarioId, String activatedBy) {
        this.mockModeEnabled = true;
        this.scenarioId      = scenarioId;
        this.activatedBy     = activatedBy;
        this.activatedAt     = Instant.now();
    }
}
