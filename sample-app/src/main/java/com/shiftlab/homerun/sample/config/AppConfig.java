package com.shiftlab.homerun.sample.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiftlab.homerun.batter.HomeRunBatter;
import com.shiftlab.homerun.batter.context.RequestMockContext;
import com.shiftlab.homerun.batter.store.ExpectationStore;

import com.shiftlab.homerun.sample.client.MockPostClient;
import com.shiftlab.homerun.sample.client.PostClient;
import com.shiftlab.homerun.sample.client.RealPostClient;

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
