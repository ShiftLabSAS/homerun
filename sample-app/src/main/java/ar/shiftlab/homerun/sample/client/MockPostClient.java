package ar.shiftlab.homerun.sample.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import ar.shiftlab.homerun.batter.context.RequestMockContext;
import ar.shiftlab.homerun.batter.store.ExpectationStore;
import ar.shiftlab.homerun.batter.support.MockClientSupport;

import ar.shiftlab.homerun.sample.model.Post;

/**
 * Mock implementation of {@link PostClient} backed by the homerun expectation
 * store.
 *
 * <p>
 * Registered as a bean in {@link ar.shiftlab.homerun.sample.config.AppConfig}. Batter
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
