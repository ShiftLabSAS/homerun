package com.homerun.sample.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homerun.batter.context.RequestMockContext;
import com.homerun.batter.store.ExpectationStore;
import com.homerun.batter.support.MockClientSupport;

import com.homerun.sample.model.Post;

/**
 * Mock implementation of {@link PostClient} backed by the homerun expectation
 * store.
 *
 * <p>
 * Registered as a bean in {@link com.homerun.sample.config.AppConfig}. Batter
 * auto-discovers it and routes mock-mode requests here instead of the real HTTP
 * client.
 */
public class MockPostClient extends MockClientSupport implements PostClient {

    public MockPostClient(ExpectationStore store,
            RequestMockContext ctx,
            ObjectMapper mapper) {
        super(store, ctx, mapper, false);
    }

    /**
     * Must match the {@code targetService} used in pitcher's {@code expect()} call.
     */
    @Override
    protected String serviceKey() {
        return "jsonplaceholder";
    }

    @Override
    public Class<?> targetInterface() {
        return PostClient.class;
    }

    @Override
    public Post getPost(int id) {
        return executeWithExpectation("getPost", id, Post.class);
    }
}
