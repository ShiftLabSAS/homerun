package ar.shiftlab.homerun.batter.context;

import java.util.UUID;

/**
 * ThreadLocal snapshot of {@link RequestMockContext} for propagation into
 * async tasks that execute outside the original request-scoped thread.
 *
 * <p>The filter populates this before delegating and clears it in {@code finally}.
 * Async executors configured via {@link ar.shiftlab.homerun.batter.async.MockContextTaskDecorator}
 * copy the snapshot onto the worker thread before the task runs and remove it after.
 *
 * <p>Mock client implementations should prefer the injected {@link RequestMockContext}
 * proxy; fall back to this holder only from threads that cannot access the request scope.
 */
public final class RequestMockContextHolder {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    public static void set(Snapshot snapshot) { HOLDER.set(snapshot); }
    public static Snapshot get()              { return HOLDER.get();  }
    public static void clear()                { HOLDER.remove();      }

    private RequestMockContextHolder() {}

    /**
     * Immutable point-in-time copy of the mock context, safe to hand to another thread.
     */
    public record Snapshot(boolean mockModeEnabled, UUID scenarioId, String activatedBy) {

        public static Snapshot of(RequestMockContext ctx) {
            return new Snapshot(ctx.isMockModeEnabled(), ctx.getScenarioId(), ctx.getActivatedBy());
        }

        public static Snapshot inactive() {
            return new Snapshot(false, null, null);
        }
    }
}
