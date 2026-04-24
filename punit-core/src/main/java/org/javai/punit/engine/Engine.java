package org.javai.punit.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.MatchResult;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.ValueMatcher;
import org.javai.punit.api.typed.spec.Configuration;
import org.javai.punit.api.typed.spec.EngineOutcome;
import org.javai.punit.api.typed.spec.ExceptionPolicy;
import org.javai.punit.api.typed.spec.SampleExecutor;
import org.javai.punit.api.typed.spec.SampleObserver;
import org.javai.punit.api.typed.spec.SampleSummary;
import org.javai.punit.api.typed.spec.Spec;

/**
 * The one engine, shared by every spec flavour.
 *
 * <p>The engine iterates
 * {@link Spec#configurations() spec.configurations()}, samples each
 * configuration through a {@link SampleExecutor}, hands the resulting
 * {@link SampleSummary} to
 * {@link Spec#consume(Configuration, SampleSummary) spec.consume(...)},
 * and finally invokes {@link Spec#conclude() spec.conclude()} to
 * obtain the run's {@link EngineOutcome}.
 *
 * <p>Resource controls declared on the spec (time budget, token
 * budget, static token charge, example-failure cap) are honoured here
 * via a {@link BudgetTracker} that the executor consults before each
 * sample. Latency percentiles are computed for every configuration
 * regardless of whether the spec declared latency thresholds —
 * enforcement is optional, computation is not.
 *
 * <p>No {@code instanceof} / {@code switch} on spec subtype anywhere
 * in this class or its helpers.
 */
public final class Engine {

    private final SampleExecutor executor;

    public Engine() {
        this(new SerialSampleExecutor());
    }

    public Engine(SampleExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public <FT, IT, OT> EngineOutcome run(Spec<FT, IT, OT> spec) {
        Objects.requireNonNull(spec, "spec");
        Iterator<Configuration<FT, IT, OT>> it = spec.configurations();
        int cycleStart = 0;
        while (it.hasNext()) {
            Configuration<FT, IT, OT> cfg = it.next();
            UseCase<FT, IT, OT> uc = spec.useCaseFactory().apply(cfg.factors());
            SampleSummary<OT> summary = runConfig(spec, uc, cfg, cycleStart);
            spec.consume(cfg, summary);
            cycleStart = (cycleStart + summary.total()) % cfg.inputs().size();
        }
        return spec.conclude();
    }

    private <FT, IT, OT> SampleSummary<OT> runConfig(
            Spec<FT, IT, OT> spec,
            UseCase<FT, IT, OT> useCase,
            Configuration<FT, IT, OT> cfg,
            int cycleStart) {

        BudgetTracker tracker = new BudgetTracker(
                spec.timeBudget(), spec.tokenBudget(), spec.tokenCharge());
        Aggregator<OT> agg = new Aggregator<>(
                cfg.expected(),
                cycleStart,
                spec.matcher(),
                spec.exceptionPolicy(),
                spec.maxExampleFailures(),
                tracker);

        long t0 = System.nanoTime();
        executor.runSamples(
                useCase,
                cfg.inputs(),
                cfg.samples(),
                cycleStart,
                agg,
                tracker::shouldStopBeforeNextSample);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
        return agg.toSummary(elapsed);
    }

    /**
     * Aggregates per-sample outcomes. Stamps the per-sample duration
     * onto each outcome, attaches a {@link MatchResult} when the spec
     * supplied a matcher, records the token cost on the tracker, and
     * caps retained failure detail at {@code maxExampleFailures}.
     */
    private static final class Aggregator<OT> implements SampleObserver<OT> {

        private final List<OT> expected;
        private final int cycleStart;
        private final Optional<ValueMatcher<OT>> matcher;
        private final ExceptionPolicy exceptionPolicy;
        private final int maxExampleFailures;
        private final BudgetTracker tracker;
        // retained is exposed via the summary; failure detail beyond
        // maxExampleFailures is elided. allForLatency holds durations
        // from every sample so latency stats never skew on drop.
        private final List<UseCaseOutcome<OT>> retained = new ArrayList<>();
        private final List<UseCaseOutcome<OT>> allForLatency = new ArrayList<>();
        private int successes = 0;
        private int failures = 0;
        private int retainedFailures = 0;
        private long tokensConsumed = 0L;
        private int failuresDropped = 0;

        Aggregator(List<OT> expected,
                   int cycleStart,
                   Optional<ValueMatcher<OT>> matcher,
                   ExceptionPolicy exceptionPolicy,
                   int maxExampleFailures,
                   BudgetTracker tracker) {
            this.expected = expected;
            this.cycleStart = cycleStart;
            this.matcher = matcher;
            this.exceptionPolicy = exceptionPolicy;
            this.maxExampleFailures = maxExampleFailures;
            this.tracker = tracker;
        }

        @Override
        public void onSample(int index, UseCaseOutcome<OT> outcome, Duration elapsed) {
            UseCaseOutcome<OT> stamped = outcome.withDuration(elapsed);
            stamped = maybeAttachMatch(index, stamped);
            record(stamped);
        }

        @Override
        public void onDefect(int index, Throwable throwable, Duration elapsed) {
            // Errors (OOM, StackOverflow, LinkageError) always propagate
            // — they are not caught regardless of the spec's policy.
            if (throwable instanceof Error e) {
                throw e;
            }
            if (exceptionPolicy == ExceptionPolicy.ABORT_TEST) {
                if (throwable instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(throwable);
            }
            // FAIL_SAMPLE: synthesise a failing outcome and continue.
            UseCaseOutcome<OT> synthetic = UseCaseOutcome
                    .<OT>fail("defect", throwable.toString())
                    .withDuration(elapsed);
            record(synthetic);
        }

        private void record(UseCaseOutcome<OT> stamped) {
            tracker.recordSampleTokens(stamped.tokens());
            allForLatency.add(stamped);
            boolean ok = stamped.value().isOk();
            if (ok) {
                successes++;
                retained.add(stamped);
            } else {
                failures++;
                if (retainedFailures < maxExampleFailures) {
                    retained.add(stamped);
                    retainedFailures++;
                } else {
                    failuresDropped++;
                }
            }
            tokensConsumed += stamped.tokens() + tracker.tokenCharge();
        }

        private UseCaseOutcome<OT> maybeAttachMatch(int index, UseCaseOutcome<OT> outcome) {
            if (expected.isEmpty() || matcher.isEmpty()) {
                return outcome;
            }
            int cycleIndex = (cycleStart + index) % expected.size();
            OT expectedValue = expected.get(cycleIndex);
            OT actualValue = outcome.rawResult();
            MatchResult result = matcher.get().match(expectedValue, actualValue);
            return outcome.withMatch(result);
        }

        SampleSummary<OT> toSummary(Duration elapsed) {
            LatencyResult latencyResult = LatencyPercentileComputer.computeFrom(allForLatency);
            return new SampleSummary<>(
                    retained,
                    elapsed,
                    successes,
                    failures,
                    tokensConsumed,
                    failuresDropped,
                    latencyResult,
                    tracker.terminationReason());
        }
    }
}
