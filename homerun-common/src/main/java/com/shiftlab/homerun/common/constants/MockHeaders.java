package com.shiftlab.homerun.common.constants;

/**
 * HTTP header names used to activate mock mode on the server side.
 */
public final class MockHeaders {

    /** Set to {@code "true"} to enable mock mode for the request. */
    public static final String MOCK_MODE        = "X-Mock-Mode";

    /**
     * UUID of the scenario whose expectations should be used.
     * Required when {@link #MOCK_MODE} is {@code "true"}.
     */
    public static final String MOCK_SCENARIO_ID = "X-Mock-Scenario-Id";

    /**
     * Shared secret token required to activate mock mode.
     * Validated by the server before honouring the other headers.
     */
    public static final String MOCK_AUTH_TOKEN  = "X-Mock-Auth-Token";

    private MockHeaders() {}
}
