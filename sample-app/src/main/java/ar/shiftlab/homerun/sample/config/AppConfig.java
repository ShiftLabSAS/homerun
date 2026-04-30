package ar.shiftlab.homerun.sample.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ar.shiftlab.homerun.batter.HomeRunBatter;
import ar.shiftlab.homerun.batter.context.RequestMockContext;
import ar.shiftlab.homerun.batter.store.ExpectationStore;

import ar.shiftlab.homerun.sample.client.MockPostClient;
import ar.shiftlab.homerun.sample.client.PostClient;
import ar.shiftlab.homerun.sample.client.RealPostClient;

@Configuration
public class AppConfig {

    @Bean
    public HomeRunBatter homeRunBatter(
            @Value("${mongo.user}") String user,
            @Value("${mongo.password}") String password,
            @Value("${mongo.host}") String host) {
        return HomeRunBatter.builder()
                .user(user)
                .password(password)
                .host(host)
                .build();
    }

    @Bean
    public PostClient postClient(HomeRunBatter batter,
            ExpectationStore expectationStore,
            RequestMockContext requestMockContext,
            ObjectMapper objectMapper,
            @Value("${jsonplaceholder.base-url}") String baseUrl) {
        return batter.route(PostClient.class,
                new RealPostClient(baseUrl),
                new MockPostClient(expectationStore, requestMockContext, objectMapper));
    }
}
