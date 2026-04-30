package ar.shiftlab.homerun.pitcher.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for the homerun-pitcher library.
 *
 * <pre>{@code
 * homerun:
 *   pitcher:
 *     enabled: true
 *     default-ttl: 1h
 *     default-created-by: ci-integration-tests
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "homerun.pitcher")
public class PitcherProperties {

    /** Must be set to {@code true} to activate the pitcher beans. */
    private boolean enabled = false;

    /**
     * Default TTL applied to every expectation when no explicit {@code ttl} or
     * {@code expiresAt} is set on the {@link ar.shiftlab.homerun.pitcher.PitchRequest}.
     */
    private Duration defaultTtl = Duration.ofHours(2);

    /**
     * Default value for the {@code createdBy} field when no explicit value is
     * provided on the request. Useful for tagging CI job identity.
     */
    private String defaultCreatedBy = "homerun-pitcher";

    /**
     * Shared secret sent as {@code X-Mock-Auth-Token} by
     * {@link ar.shiftlab.homerun.pitcher.PitcherClient}
     * HTTP helpers. Must match {@code homerun.batter.auth-token} on the server
     * side.
     */
    private String authToken;

    /**
     * Base URL of the application under test, used when no {@code TestRestTemplate}
     * is available in the context (i.e. {@code WebEnvironment.NONE}).
     * Example: {@code http://localhost:8080}.
     */
    private String baseUrl = "http://localhost:8080";
}
