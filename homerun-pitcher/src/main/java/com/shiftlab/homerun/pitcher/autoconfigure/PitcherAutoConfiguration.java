package com.shiftlab.homerun.pitcher.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftlab.homerun.pitcher.PitcherClient;
import com.shiftlab.homerun.pitcher.repository.PitcherExpectationRepository;

/**
 * Spring Boot auto-configuration for homerun-pitcher.
 *
 * <p>
 * Activated by setting {@code homerun.pitcher.enabled=true} in the test
 * application context (e.g. {@code application-inttest.yml}).
 */
@AutoConfiguration
@ConditionalOnProperty(name = "homerun.pitcher.enabled", havingValue = "true")
@EnableConfigurationProperties(PitcherProperties.class)
@EnableMongoRepositories(basePackageClasses = PitcherExpectationRepository.class, mongoTemplateRef = "batterMongoTemplate")
public class PitcherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PitcherClient pitcherClient(PitcherExpectationRepository repository,
            ObjectMapper objectMapper,
            PitcherProperties properties,
            ObjectProvider<TestRestTemplate> testRestTemplate) {
        // Extract the underlying RestTemplate from TestRestTemplate when available.
        // TestRestTemplate already has a DefaultUriBuilderFactory pointing at
        // http://localhost:{port}, so relative paths like "/posts/1" resolve correctly.
        TestRestTemplate trt = testRestTemplate.getIfAvailable();
        RestTemplate rt;
        if (trt != null) {
            // Embedded server — TestRestTemplate already has localhost:{randomPort} wired
            // in.
            rt = trt.getRestTemplate();
        } else {
            // External server — build a RestTemplate whose base URL is pitcher.base-url.
            rt = new RestTemplate();
            rt.setUriTemplateHandler(new DefaultUriBuilderFactory(properties.getBaseUrl()));
        }
        return new PitcherClient(repository, objectMapper, properties, rt);
    }
}
