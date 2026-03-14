package com.homerun.batter;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.homerun.batter.context.RequestMockContext;
import com.homerun.batter.context.RequestMockContextHolder;
import com.homerun.batter.support.MockClientSupport;

import lombok.extern.slf4j.Slf4j;

/**
 * Primary API for consuming applications to integrate with the mock
 * infrastructure.
 *
 * <h3>Pattern A — builder (simplest)</h3>
 * Declare a single {@link HomeRunBatter} bean using the fluent builder.
 * Batter's auto-configuration will detect it and wire up the full
 * infrastructure
 * (MongoDB client, expectation store, request filter) automatically.
 *
 * <pre>{@code
 * &#64;Bean
 * public HomeRunBatter homeRunBatter() {
 *     return HomeRunBatter.builder()
 *             .user("user")
 *             .password("pass")
 *             .host("mongo-host:27017")
 *             .build();
 * }
 *
 * &#64;Bean
 * public TwitterService twitterService(HomeRunBatter batter) {
 *     return batter.isActive()
 *             ? batter.mockService(TwitterService.class)
 *             : new TwitterServiceImpl();
 * }
 * }</pre>
 *
 * <h3>Pattern B — {@code @RequestScope} bean (per-request wiring)</h3>
 * Evaluated fresh on every request so {@link #isActive()} reflects the live
 * context at the moment the bean is created.
 *
 * <pre>{@code
 * &#64;Bean
 * @RequestScope
 * public TwitterService twitterService(HomeRunBatter batter) {
 *     return batter.isActive()
 *             ? batter.mockService(TwitterService.class)
 *             : new RealTwitterServiceClient();
 * }
 * }
 * </pre>
 *
 * <h3>Pattern C — singleton proxy via {@link #route(Class, Object)} (zero
 * request-scope)</h3>
 * Returns a JDK dynamic proxy created once at startup that routes each call
 * to mock or real at invocation time.
 *
 * <pre>{@code
 * @Bean
 * public TwitterService twitterService(HomeRunBatter batter,
 *         RealTwitterServiceClient realClient) {
 *     return batter.route(TwitterService.class, realClient);
 * }
 * }</pre>
 */
@Slf4j
public class HomeRunBatter {

    /**
     * MongoDB connection details captured from the {@link Builder}.
     * Used internally by batter's auto-configuration to create the MongoDB client.
     *
     * @param username MongoDB authentication username
     * @param password MongoDB authentication password
     * @param host     MongoDB host, optionally including port (e.g.
     *                 {@code mongo-host:27017})
     */
    public record ConnectionConfig(String username, String password, String host) {
    }

    private RequestMockContext requestMockContext;
    private Map<Class<?>, MockClientSupport> registry;
    private final ConnectionConfig connectionConfig;

    /**
     * Built by {@link com.homerun.batter.autoconfigure.BatterAutoConfiguration}
     * when activated via {@code homerun.batter.enabled=true}.
     */
    public HomeRunBatter(RequestMockContext requestMockContext,
            List<MockClientSupport> mockSupports) {
        this.connectionConfig = null;
        initialize(requestMockContext, mockSupports);
    }

    private HomeRunBatter(Builder builder) {
        this.connectionConfig = new ConnectionConfig(builder.user, builder.password, builder.host);
        this.registry = Map.of();
    }

    /**
     * Returns a fluent builder for constructing a {@link HomeRunBatter}.
     * Declare the result as a {@code @Bean} — batter's auto-configuration detects
     * it and wires up the MongoDB client, expectation store and request filter:
     *
     * <pre>{@code
     * @Bean
     * public HomeRunBatter homeRunBatter() {
     *     return HomeRunBatter.builder()
     *             .user("user")
     *             .password("pass")
     *             .host("mongo-host:27017")
     *             .build();
     * }
     * }</pre>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link HomeRunBatter}.
     */
    public static final class Builder {

        private String user;
        private String password;
        private String host;

        private Builder() {
        }

        /** MongoDB authentication username. */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /** MongoDB authentication password. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * MongoDB host, optionally including the port
         * (e.g. {@code mongo-host:27017}).
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public HomeRunBatter build() {
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("HomeRunBatter.builder(): host() is required");
            }
            return new HomeRunBatter(this);
        }
    }

    /**
     * Called by batter's auto-configuration once the request context and mock
     * registry are available. Idempotent — safe to call even on instances
     * created via the standard constructor.
     */
    public void initialize(RequestMockContext ctx, List<MockClientSupport> supports) {
        if (this.requestMockContext != null)
            return; // already initialized
        this.requestMockContext = ctx;
        this.registry = buildRegistry(supports);
        log.debug("HomeRunBatter initialized — mocks: {}", registry.keySet().stream()
                .map(Class::getSimpleName).collect(Collectors.joining(", ")));
    }

    /**
     * Returns the connection config when created via {@link #create}; {@code null}
     * otherwise.
     */
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    // ── Core API ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the current HTTP request carries active mock
     * headers.
     *
     * <p>
     * Safe to call outside a request context (e.g. at startup, in background
     * threads) — returns {@code false} instead of throwing.
     */
    public boolean isActive() {
        // ThreadLocal is populated by MockHeaderFilter before the request scope
        // is active; check it first so routing works on the servlet thread.
        RequestMockContextHolder.Snapshot snapshot = RequestMockContextHolder.get();
        if (snapshot != null && snapshot.mockModeEnabled()) {
            return true;
        }
        if (requestMockContext == null)
            return false;
        try {
            return requestMockContext.isMockModeEnabled();
        } catch (Exception e) {
            // Request-scoped proxy throws when accessed outside a live request.
            return false;
        }
    }

    /**
     * Returns the registered {@link MockClientSupport} that implements
     * {@code serviceInterface}.
     *
     * <p>
     * Intended for use inside a {@code @RequestScope} bean factory or inside
     * a service method, always guarded by {@link #isActive()}:
     *
     * <pre>{@code
     * batter.isActive()
     *         ? batter.mockService(TwitterService.class)
     *         : realTwitterService
     * }</pre>
     *
     * @throws IllegalArgumentException if no mock is registered for the interface
     */
    @SuppressWarnings("unchecked")
    public <T> T mockService(Class<T> serviceInterface) {
        MockClientSupport mock = registry.get(serviceInterface);
        if (mock == null) {
            throw new IllegalArgumentException(
                    "No mock registered for [%s]. Ensure a MockClientSupport bean "
                            + "declaring targetInterface() = %s is present in the context."
                                    .formatted(serviceInterface.getName(), serviceInterface.getSimpleName()));
        }
        return (T) mock;
    }

    /**
     * Creates a singleton JDK dynamic proxy that routes each method invocation
     * to the mock or real implementation based on the live request context.
     *
     * <p>
     * This is the preferred wiring pattern for singleton {@code @Bean} definitions
     * because the proxy is created once and needs no {@code @RequestScope}:
     *
     * <pre>{@code
     * @Bean
     * public TwitterService twitterService(HomeRunBatter batter,
     *         RealTwitterServiceClient realClient) {
     *     return batter.route(TwitterService.class, realClient);
     * }
     * }</pre>
     *
     * <p>
     * At each invocation the proxy checks {@link #isActive()}: if {@code true}
     * it delegates to the registered mock; if {@code false} it delegates to
     * {@code realImpl}. Both branches are proper Spring-managed beans, so their
     * own dependencies and lifecycle are unaffected.
     *
     * <p>
     * Requires {@code serviceInterface} to be a Java interface. If your real
     * implementation is a concrete class with no interface, use Pattern A with
     * {@code @RequestScope} instead.
     *
     * @param serviceInterface the interface the proxy should implement
     * @param realImpl         the real singleton implementation to use outside mock
     *                         mode
     * @throws IllegalArgumentException if {@code serviceInterface} is not an
     *                                  interface,
     *                                  or if no mock is registered for it
     */
    @SuppressWarnings("unchecked")
    public <T> T route(Class<T> serviceInterface, T realImpl) {
        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException(
                    "route() requires an interface type, but got: " + serviceInterface.getName()
                            + ". Use @RequestScope with isActive()/mockService() for concrete classes.");
        }

        T mock = mockService(serviceInterface); // fail-fast at wiring time if missing

        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[] { serviceInterface },
                (proxy, method, args) -> {
                    T target = isActive() ? mock : realImpl;
                    return method.invoke(target, args);
                });
    }

    /**
     * Variant of {@link #route(Class, Object)} that accepts the mock
     * implementation directly, bypassing the internal registry. Use this in
     * {@code @Bean} factory methods where the mock is constructed inline rather
     * than registered as a separate Spring-managed bean.
     */
    @SuppressWarnings("unchecked")
    public <T> T route(Class<T> serviceInterface, T realImpl, T mockImpl) {
        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException(
                    "route() requires an interface type, but got: " + serviceInterface.getName());
        }
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[] { serviceInterface },
                (proxy, method, args) -> {
                    T target = isActive() ? mockImpl : realImpl;
                    return method.invoke(target, args);
                });
    }

    /**
     * Returns {@code true} if a mock is registered for the given interface.
     * Useful for conditional wiring or diagnostics.
     */
    public boolean hasMock(Class<?> serviceInterface) {
        return registry.containsKey(serviceInterface);
    }

    private static Map<Class<?>, MockClientSupport> buildRegistry(List<MockClientSupport> supports) {
        return supports.stream().collect(Collectors.toMap(
                MockClientSupport::targetInterface,
                Function.identity(),
                (a, b) -> {
                    log.warn("Duplicate mock registered for interface {}; keeping first: {}",
                            a.targetInterface(), a.getClass().getSimpleName());
                    return a;
                }));
    }
}
