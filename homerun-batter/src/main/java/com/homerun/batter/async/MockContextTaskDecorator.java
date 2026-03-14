package com.homerun.batter.async;

import com.homerun.batter.context.RequestMockContextHolder;
import com.homerun.batter.context.RequestMockContextHolder.Snapshot;
import org.springframework.core.task.TaskDecorator;

/**
 * Propagates the mock context snapshot onto async worker threads so that mock
 * client implementations resolve the correct expectation even when called from
 * {@code @Async} methods or {@link java.util.concurrent.CompletableFuture} chains.
 *
 * <h3>Registration</h3>
 * <pre>{@code
 * @Bean
 * public Executor asyncExecutor(MockContextTaskDecorator decorator) {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(decorator);
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 */
public class MockContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Snapshot snapshot = RequestMockContextHolder.get();
        return () -> {
            try {
                if (snapshot != null) {
                    RequestMockContextHolder.set(snapshot);
                }
                runnable.run();
            } finally {
                RequestMockContextHolder.clear();
            }
        };
    }
}
