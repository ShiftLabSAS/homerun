package ar.shiftlab.homerun.batter.autoconfigure;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for the homerun-batter library.
 *
 * <pre>{@code
 * # application-inttest.yml
 * homerun:
 *   batter:
 *     enabled: true
 *     auth-token: ${MOCK_AUTH_TOKEN}
 *     allowed-ip-ranges:
 *       - "10.0.0.0"
 *       - "172.16."
 *     strict-request-matching: false
 *     mongo:
 *       host: ${MONGO_HOST}   # include port if non-default, e.g. mongo-host:27017
 *       username: ${MONGO_USER}
 *       password: ${MONGO_PASS}
 *       database: ${MONGO_DB} # batter will use "homerun_<database>"
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "homerun.batter")
public class BatterProperties {

    /**
     * Must be {@code true} to activate all batter beans. Set {@code false} in prod.
     */
    private boolean enabled = false;

    /**
     * Shared secret token that callers must supply via {@code X-Mock-Auth-Token}.
     * Should be sourced from a Vault/k8s secret, never hardcoded.
     */
    private String authToken;

    /**
     * Optional CIDR/prefix allowlist. When empty, all remote IPs are accepted
     * (useful for local development). In deployed envs, restrict to CI/VPN ranges.
     */
    private List<String> allowedIpRanges = List.of();

    /**
     * When {@code true}, the actual request sent to a mock client is compared
     * against the {@code serializedRequest} stored in the expectation. Mismatches
     * throw {@link ar.shiftlab.homerun.batter.exception.MockRequestMismatchException}.
     * Leave {@code false} (default) for loose scenario-based matching.
     */
    private boolean strictRequestMatching = false;

    /** MongoDB connection settings used by batter's own expectation store. */
    private Mongo mongo = new Mongo();

    /**
     * Connection details for the MongoDB database that backs the expectation store.
     * The consuming application is responsible for provisioning the MongoDB
     * instance
     * and supplying these values. Batter will automatically use a database named
     * {@code homerun_<database>} to keep its collections isolated from the rest of
     * the application's data.
     */
    @Data
    public static class Mongo {

        /**
         * MongoDB host, optionally including the port
         * (e.g. {@code mongo-host:27017}). Required.
         */
        private String host;

        /**
         * Database name supplied by the consuming application. Batter will prepend
         * {@code homerun_} automatically, so the actual database used will be
         * {@code homerun_<database>}. Required.
         */
        private String database;

        /**
         * Authentication username. Should be sourced from a Vault/k8s secret,
         * never hardcoded in {@code application.yml}.
         */
        private String username;

        /**
         * Authentication password. Should be sourced from a Vault/k8s secret,
         * never hardcoded in {@code application.yml}.
         */
        private String password;
    }
}
