package org.javai.punit.experiment.engine;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;

/**
 * Shared warmup handling for experiment mode strategies.
 *
 * <p>All experiment modes (MEASURE, EXPLORE, OPTIMIZE) follow the same warmup
 * pattern: check whether we are still in the warmup phase, execute the invocation
 * but discard its result, and optionally check a time budget during warmup.
 *
 * <p>Public to allow use by strategies in sibling packages (e.g. measure, explore, optimize).
 */
public class ExperimentWarmupHandler {

    /**
     * Result of warmup handling.
     *
     * @param handled true if this invocation was consumed as a warmup (caller should return)
     * @param terminatedDuringWarmup true if a budget was exhausted during warmup
     */
    public record WarmupResult(boolean handled, boolean terminatedDuringWarmup) {

        static final WarmupResult NOT_IN_WARMUP = new WarmupResult(false, false);
        static final WarmupResult HANDLED = new WarmupResult(true, false);
        static final WarmupResult TERMINATED_DURING_WARMUP = new WarmupResult(true, true);
    }

    /**
     * Handles the warmup gate for an experiment invocation.
     *
     * <p>If the warmup phase is active (counter &lt; warmup), the invocation is
     * executed and its result discarded. If a time budget is configured and
     * exceeded during warmup, the aggregator and terminated flag are updated.
     *
     * @param invocation the JUnit invocation to proceed or skip
     * @param store the extension store containing warmupCounter, terminated, aggregator
     * @param warmup the number of warmup invocations configured (0 = no warmup)
     * @param timeBudgetMs the time budget in milliseconds (0 = unlimited)
     * @param startTimeMs the experiment start time in milliseconds (may be null if no time budget)
     * @return the warmup result indicating whether the invocation was consumed
     */
    public WarmupResult handle(
            InvocationInterceptor.Invocation<Void> invocation,
            ExtensionContext.Store store,
            int warmup,
            long timeBudgetMs,
            Long startTimeMs) {

        if (warmup <= 0) {
            return WarmupResult.NOT_IN_WARMUP;
        }

        AtomicInteger warmupCounter = store.get("warmupCounter", AtomicInteger.class);
        if (warmupCounter == null || warmupCounter.get() >= warmup) {
            return WarmupResult.NOT_IN_WARMUP;
        }

        // Check time budget during warmup
        if (timeBudgetMs > 0 && startTimeMs != null) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            if (elapsed >= timeBudgetMs) {
                AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);
                ExperimentResultAggregator aggregator =
                        store.get("aggregator", ExperimentResultAggregator.class);
                if (terminated != null) {
                    terminated.set(true);
                }
                if (aggregator != null) {
                    aggregator.setTerminated("TIME_BUDGET_EXHAUSTED",
                            "Time budget of " + timeBudgetMs + "ms exceeded during warmup");
                }
                try {
                    invocation.skip();
                } catch (Throwable t) {
                    // skip() should not throw, but guard defensively
                }
                return WarmupResult.TERMINATED_DURING_WARMUP;
            }
        }

        // Execute warmup — invoke but discard result
        try {
            invocation.proceed();
        } catch (Throwable t) {
            // Silently discard warmup failures
        }
        warmupCounter.incrementAndGet();

        return WarmupResult.HANDLED;
    }
}
