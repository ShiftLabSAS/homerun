package com.shiftlab.homerun.batter.filter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shiftlab.homerun.batter.context.RequestMockContext;
import com.shiftlab.homerun.batter.context.RequestMockContextHolder;
import com.shiftlab.homerun.batter.security.MockAuthValidator;
import com.shiftlab.homerun.common.constants.MockHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Intercepts every HTTP request and, when mock-mode headers are present and
 * authorized, activates the {@link RequestMockContext} for that request.
 *
 * <p>
 * If the headers are absent the filter is a pure no-op with zero overhead on
 * normal production traffic. If the headers are present but invalid (bad token,
 * missing scenario ID, malformed UUID) the filter responds with {@code 400} or
 * {@code 403} and does not forward to the rest of the chain.
 *
 * <p>
 * The filter also populates {@link RequestMockContextHolder} so that async
 * tasks decorated by {@link com.shiftlab.homerun.batter.async.MockContextTaskDecorator}
 * can access the scenario ID on non-request threads.
 */
@Slf4j
@RequiredArgsConstructor
public class MockHeaderFilter extends OncePerRequestFilter {

    private final MockAuthValidator authValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String mockModeHeader = request.getHeader(MockHeaders.MOCK_MODE);

        if (!"true".equalsIgnoreCase(mockModeHeader)) {
            chain.doFilter(request, response);
            return;
        }

        // ── Validate scenario ID ───────────────────────────────────────────────
        String rawScenarioId = request.getHeader(MockHeaders.MOCK_SCENARIO_ID);
        if (!StringUtils.hasText(rawScenarioId)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(),
                    MockHeaders.MOCK_SCENARIO_ID + " header is required when mock mode is active");
            return;
        }

        UUID scenarioId;
        try {
            scenarioId = UUID.fromString(rawScenarioId);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpStatus.BAD_REQUEST.value(),
                    MockHeaders.MOCK_SCENARIO_ID + " must be a valid UUID");
            return;
        }

        // ── Validate auth token ────────────────────────────────────────────────
        String authToken = request.getHeader(MockHeaders.MOCK_AUTH_TOKEN);
        if (!authValidator.isAuthorized(authToken, request)) {
            response.sendError(HttpStatus.FORBIDDEN.value(),
                    "Mock mode requires a valid " + MockHeaders.MOCK_AUTH_TOKEN);
            return;
        }

        // ── Activate context ───────────────────────────────────────────────────
        // Populate the ThreadLocal only — do NOT touch the @RequestScope proxy
        // here because the request scope is not yet active at filter time
        // (Spring's RequestContextFilter runs later in the chain).
        String principal = resolvePrincipal();
        RequestMockContextHolder.set(new RequestMockContextHolder.Snapshot(true, scenarioId, principal));

        log.debug("Mock mode activated [scenario={}, by={}]", scenarioId, principal);

        try {
            chain.doFilter(request, response);
        } finally {
            RequestMockContextHolder.clear();
        }
    }

    private String resolvePrincipal() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName)
                .orElse("anonymous");
    }
}
