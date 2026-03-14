package com.homerun.batter.autoconfigure;

import java.util.List;

import org.bson.UuidRepresentation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.context.annotation.RequestScope;

import com.homerun.batter.HomeRunBatter;
import com.homerun.batter.async.MockContextTaskDecorator;
import com.homerun.batter.audit.MockInvocationAudit;
import com.homerun.batter.context.RequestMockContext;
import com.homerun.batter.filter.MockHeaderFilter;
import com.homerun.batter.security.MockAuthValidator;
import com.homerun.batter.store.ExpectationMongoRepository;
import com.homerun.batter.store.ExpectationStore;
import com.homerun.batter.store.MongoExpectationStore;
import com.homerun.batter.support.MockClientSupport;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * Spring Boot auto-configuration for homerun-batter.
 *
 * <p>
 * Activated by {@code homerun.batter.enabled=true}. All beans declared here
 * are absent in production where the property is {@code false}, so the mock
 * infrastructure has zero footprint outside of integration-test environments.
 *
 * <h3>What gets registered</h3>
 * <ul>
 * <li>{@link RequestMockContext} — request-scoped, exposed as a scoped proxy so
 * singleton beans can safely hold a reference to it.</li>
 * <li>{@link MockHeaderFilter} — reads mock headers, validates auth, activates
 * context.</li>
 * <li>{@link ExpectationStore} → {@link MongoExpectationStore} — fetches and
 * audits
 * expectations from MongoDB.</li>
 * <li>{@link MockContextTaskDecorator} — propagates context to async
 * threads.</li>
 * </ul>
 *
 * <h3>What the consuming app must provide</h3>
 * <ul>
 * <li>A {@link HomeRunBatter} bean, either via
 * {@link HomeRunBatter#create(String, String, String)} (simplest) or by setting
 * {@code homerun.batter.enabled=true} in {@code application.yml} together with
 * {@code homerun.batter.mongo.*} connection properties.</li>
 * <li>Mock client beans extending
 * {@link com.homerun.batter.support.MockClientSupport},
 * each declaring {@code serviceKey()} and {@code targetInterface()}.</li>
 * <li>Service bean wiring using the injected {@link HomeRunBatter}:
 *
 * <pre>{@code
 * @Bean
 * public TwitterService twitterService(HomeRunBatter batter) {
 *     return batter.isActive()
 *             ? batter.mockService(TwitterService.class)
 *             : new TwitterServiceImpl();
 * }
 * }</pre>
 *
 * </li>
 * </ul>
 */
@AutoConfiguration
@Conditional(BatterAutoConfiguration.BatterActivationCondition.class)
@EnableConfigurationProperties(BatterProperties.class)
@EnableMongoRepositories(basePackageClasses = ExpectationMongoRepository.class, mongoTemplateRef = "batterMongoTemplate")
public class BatterAutoConfiguration {

    // ── MongoDB ───────────────────────────────────────────────────────────────

    /**
     * A {@link MongoClient} dedicated to batter, built from
     * {@code homerun.batter.mongo.*} properties provided by the consuming
     * application. Using a separate client ensures batter's expectation store
     * is fully isolated from the host application's own MongoDB configuration.
     */
    @Bean("batterMongoClient")
    @ConditionalOnMissingBean(name = "batterMongoClient")
    public MongoClient batterMongoClient(
            ObjectProvider<HomeRunBatter> batterProvider, BatterProperties properties) {
        String uri;
        HomeRunBatter batter = batterProvider.getIfAvailable();
        if (batter != null && batter.getConnectionConfig() != null) {
            HomeRunBatter.ConnectionConfig c = batter.getConnectionConfig();
            uri = buildMongoUri(c.username(), c.password(), c.host());
        } else {
            BatterProperties.Mongo m = properties.getMongo();
            uri = buildMongoUri(m.getUsername(), m.getPassword(), m.getHost());
        }
        MongoClientSettings settings = MongoClientSettings.builder()
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .applyConnectionString(new ConnectionString(uri))
                .build();
        return MongoClients.create(settings);
    }

    @Bean("batterMongoDatabaseFactory")
    @ConditionalOnMissingBean(name = "batterMongoDatabaseFactory")
    public MongoDatabaseFactory batterMongoDatabaseFactory(
            MongoClient batterMongoClient,
            ObjectProvider<HomeRunBatter> batterProvider,
            BatterProperties properties) {
        HomeRunBatter batter = batterProvider.getIfAvailable();
        String database = (batter != null && batter.getConnectionConfig() != null)
                ? "homerun"
                : "homerun_" + properties.getMongo().getDatabase();
        return new SimpleMongoClientDatabaseFactory(batterMongoClient, database);
    }

    @Bean("batterMongoTemplate")
    @ConditionalOnMissingBean(name = "batterMongoTemplate")
    public MongoTemplate batterMongoTemplate(MongoDatabaseFactory batterMongoDatabaseFactory) {
        return new MongoTemplate(batterMongoDatabaseFactory);
    }

    private static String buildMongoUri(String username, String password, String host) {
        StringBuilder uri = new StringBuilder("mongodb://");
        if (username != null && !username.isBlank()) {
            uri.append(username).append(':').append(password).append('@');
        }
        // host already includes the port when non-default, e.g. "mongo-host:27017"
        uri.append(host);
        return uri.toString();
    }

    // ── Request-scoped context ────────────────────────────────────────────────

    /**
     * The scoped proxy is the critical mechanism: singleton beans (filter, store,
     * mock clients) hold a reference to the proxy, but every method call on the
     * proxy is delegated to the actual instance bound to the current HTTP request.
     */
    @Bean
    @RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    @ConditionalOnMissingBean
    public RequestMockContext requestMockContext() {
        return new RequestMockContext();
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public MockAuthValidator mockAuthValidator(BatterProperties properties) {
        return new MockAuthValidator(properties);
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public MockHeaderFilter mockHeaderFilter(MockAuthValidator mockAuthValidator) {
        return new MockHeaderFilter(mockAuthValidator);
    }

    @Bean
    public FilterRegistrationBean<MockHeaderFilter> mockHeaderFilterRegistration(
            MockHeaderFilter filter) {
        FilterRegistrationBean<MockHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        // Run as early as possible so the context is populated before any
        // business-layer bean has a chance to call a downstream client.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    // ── Expectation store ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public MockInvocationAudit mockInvocationAudit() {
        return new MockInvocationAudit();
    }

    @Bean
    @ConditionalOnMissingBean(ExpectationStore.class)
    public ExpectationStore expectationStore(ExpectationMongoRepository repository,
            MockInvocationAudit audit) {
        return new MongoExpectationStore(repository, audit);
    }

    // ── Async support ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public MockContextTaskDecorator mockContextTaskDecorator() {
        return new MockContextTaskDecorator();
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Collects every {@link MockClientSupport} bean in the application context
     * and builds the {@link HomeRunBatter} registry. Skipped when the consuming
     * app provides its own bean via {@link HomeRunBatter#create}.
     */
    @Bean
    @ConditionalOnMissingBean
    public HomeRunBatter homeRunBatter(RequestMockContext requestMockContext,
            List<MockClientSupport> mockSupports) {
        return new HomeRunBatter(requestMockContext, mockSupports);
    }

    /**
     * When the consumer provides a {@link HomeRunBatter} bean via
     * {@link HomeRunBatter#create}, this finalizes it by injecting the
     * request context and mock registry once all infrastructure beans are ready.
     */
    @Bean
    public SmartInitializingSingleton batterWiring(
            HomeRunBatter homeRunBatter,
            RequestMockContext requestMockContext,
            List<MockClientSupport> mockSupports) {
        return () -> homeRunBatter.initialize(requestMockContext, mockSupports);
    }

    // ── Activation condition
    // ─────────────────────────────────────────────────────────

    /**
     * Activates batter's auto-configuration when either:
     * <ul>
     * <li>{@code homerun.batter.enabled=true} is set in the environment, or</li>
     * <li>a {@link HomeRunBatter} bean has already been registered by the
     * consuming application (e.g. via {@link HomeRunBatter#create}).</li>
     * </ul>
     */
    static final class BatterActivationCondition extends AnyNestedCondition {

        BatterActivationCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(name = "homerun.batter.enabled", havingValue = "true")
        static class EnabledViaProperty {
        }

        @ConditionalOnBean(HomeRunBatter.class)
        static class EnabledViaBean {
        }
    }
}
