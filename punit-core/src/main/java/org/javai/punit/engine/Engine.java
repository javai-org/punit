package org.javai.punit.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.MatchResult;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.ValueMatcher;
import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.api.typed.spec.Configuration;
import org.javai.punit.api.typed.spec.EngineResult;
import org.javai.punit.api.typed.spec.ExceptionPolicy;
import org.javai.punit.api.typed.spec.FailureCount;
import org.javai.punit.api.typed.spec.FailureExemplar;
import org.javai.punit.api.typed.spec.SampleClassification;
import org.javai.punit.api.typed.spec.SampleExecutor;
import org.javai.punit.api.typed.spec.SampleObserver;
import org.javai.punit.api.typed.spec.SampleSummary;
import org.javai.punit.api.typed.spec.Trial;
import org.javai.punit.api.typed.spec.Spec;
import org.javai.punit.api.typed.spec.TypedSpec;

/**
 * The one engine, shared by every spec flavour.
 *
 * <p>The engine iterates
 * {@link Spec#configurations() spec.configurations()}, samples each
 * configuration through a {@link SampleExecutor}, hands the resulting
 * {@link SampleSummary} to
 * {@link Spec#consume(Configuration, SampleSummary) spec.consume(...)},
 * and finally invokes {@link Spec#conclude() spec.conclude()} to
 * obtain the run's {@link EngineResult}.
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
    private final BaselineProvider baselineProvider;

    public Engine() {
        this(new SerialSampleExecutor(), BaselineProvider.EMPTY);
    }

    public Engine(SampleExecutor executor) {
        this(executor, BaselineProvider.EMPTY);
    }

    public Engine(BaselineProvider baselineProvider) {
        this(new SerialSampleExecutor(), baselineProvider);
    }

    public Engine(SampleExecutor executor, BaselineProvider baselineProvider) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.baselineProvider = Objects.requireNonNull(baselineProvider, "baselineProvider");
    }

    public EngineResult run(Spec spec) {
        Objects.requireNonNull(spec, "spec");
        return spec.dispatch(new Spec.Dispatcher<EngineResult>() {
            @Override
            public <FT, IT, OT> EngineResult apply(TypedSpec<FT, IT, OT> typed) {
                return runTyped(typed);
            }
        });
    }

    private <FT, IT, OT> EngineResult runTyped(TypedSpec<FT, IT, OT> spec) {
        Iterator<Configuration<FT, IT, OT>> it = spec.configurations();
        int cycleStart = 0;
        while (it.hasNext()) {
            Configuration<FT, IT, OT> cfg = it.next();
            UseCase<FT, IT, OT> uc = spec.useCaseFactory().apply(cfg.factors());
            SampleSummary<OT> summary = runConfig(spec, uc, cfg, cycleStart);
            spec.consume(cfg, summary);
            cycleStart = (cycleStart + summary.total()) % cfg.inputs().size();
        }
        return spec.conclude(baselineProvider);
    }

    private <FT, IT, OT> SampleSummary<OT> runConfig(
            TypedSpec<FT, IT, OT> spec,
            UseCase<FT, IT, OT> useCase,
            Configuration<FT, IT, OT> cfg,
            int cycleStart) {

        BudgetTracker tracker = new BudgetTracker(
                spec.timeBudget(), spec.tokenBudget(), spec.tokenCharge());
        Aggregator<IT, OT> agg = new Aggregator<>(
                useCase,
                cfg.inputs(),
                cycleStart,
                spec.exceptionPolicy(),
                spec.maxExampleFailures(),
                tracker);

        long t0 = System.nanoTime();
        executor.runSamples(
                useCase,
                cfg.inputs(),
                cfg.expected(),
                spec.matcher(),
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
    private static final class Aggregator<IT, OT> implements SampleObserver<OT> {

        private final UseCase<?, IT, OT> useCase;
        private final List<IT> inputs;
        private final int cycleStart;
        private final ExceptionPolicy exceptionPolicy;
        private final int maxExampleFailures;
        private final BudgetTracker tracker;
        // retained is exposed via the summary; failure detail beyond
        // maxExampleFailures is elided. allForLatency holds durations
        // from every sample so latency stats never skew on drop.
        // trials carries the full ordered (input, outcome, duration)
        // history regardless of the failure cap.
        private final List<UseCaseOutcome<?, OT>> retained = new ArrayList<>();
        private final List<UseCaseOutcome<?, OT>> allForLatency = new ArrayList<>();
        private final List<Trial<?, OT>> trials = new ArrayList<>();
        private final LinkedHashMap<String, MutableFailureBucket> failuresByPostcondition =
                new LinkedHashMap<>();
        private static final int EXEMPLAR_CAP_PER_CLAUSE = 3;
        private int successes = 0;
        private int failures = 0;
        private int retainedFailures = 0;
        private long tokensConsumed = 0L;
        private int failuresDropped = 0;

        Aggregator(UseCase<?, IT, OT> useCase,
                   List<IT> inputs,
                   int cycleStart,
                   ExceptionPolicy exceptionPolicy,
                   int maxExampleFailures,
                   BudgetTracker tracker) {
            this.useCase = useCase;
            this.inputs = inputs;
            this.cycleStart = cycleStart;
            this.exceptionPolicy = exceptionPolicy;
            this.maxExampleFailures = maxExampleFailures;
            this.tracker = tracker;
        }

        @Override
        public void onSample(int index, UseCaseOutcome<?, OT> outcome, Duration elapsed) {
            // The outcome already carries duration, tokens, postcondition
            // results, and the optional match (set by Contract.apply form 3
            // when the executor is configured for matching). The classifier
            // adds the optional duration violation by reading useCase.maxLatency().
            record(index, outcome, classify(outcome));
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
            UseCaseOutcome<IT, OT> synthetic = new UseCaseOutcome<>(
                    Outcome.fail("defect", throwable.toString()),
                    useCase,
                    List.of(),
                    Optional.empty(),
                    0L,
                    elapsed);
            record(index, synthetic, classify(synthetic));
        }

        private SampleClassification classify(UseCaseOutcome<?, OT> outcome) {
            // SampleClassification.classify wants UseCaseOutcome<I, OT>; the
            // wildcard at this site is safe because all outcomes flowing
            // through this aggregator come from the executor, which only
            // produces UseCaseOutcome<IT, OT>.
            @SuppressWarnings("unchecked")
            UseCaseOutcome<IT, OT> typed = (UseCaseOutcome<IT, OT>) outcome;
            return SampleClassification.classify(useCase, typed);
        }

        private void record(int index, UseCaseOutcome<?, OT> stamped,
                            SampleClassification classification) {
            tracker.recordSampleTokens(classification.tokens());
            allForLatency.add(stamped);
            trials.add(trialFor(index, stamped));
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
            recordPostconditionFailures(index, classification);
            tokensConsumed += classification.tokens() + tracker.tokenCharge();
        }

        private void recordPostconditionFailures(int index, SampleClassification classification) {
            IT input = inputForIndex(index);
            String inputStr = String.valueOf(input);
            for (var result : classification.postconditionResults()) {
                if (!result.failed()) {
                    continue;
                }
                MutableFailureBucket bucket = failuresByPostcondition.computeIfAbsent(
                        result.description(), k -> new MutableFailureBucket());
                bucket.count++;
                if (bucket.exemplars.size() < EXEMPLAR_CAP_PER_CLAUSE) {
                    String reason = result.failureReason().orElse(result.description());
                    bucket.exemplars.add(new FailureExemplar(inputStr, reason));
                }
            }
        }

        private static final class MutableFailureBucket {
            int count = 0;
            final List<FailureExemplar> exemplars = new ArrayList<>();
        }

        private <I> Trial<IT, OT> trialFor(int index, UseCaseOutcome<I, OT> stamped) {
            // Trial<IT, OT> requires UseCaseOutcome<IT, OT>; the wildcard
            // carrier in onSample loses that I; we know the executor only
            // ever supplies UseCaseOutcome<IT, OT> so this is safe.
            @SuppressWarnings("unchecked")
            UseCaseOutcome<IT, OT> typed = (UseCaseOutcome<IT, OT>) stamped;
            return new Trial<>(inputForIndex(index), typed, stamped.duration());
        }

        private IT inputForIndex(int index) {
            int cycleIndex = (cycleStart + index) % inputs.size();
            return inputs.get(cycleIndex);
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
                    tracker.terminationReason(),
                    trials,
                    immutableFailuresByPostcondition());
        }

        private Map<String, FailureCount> immutableFailuresByPostcondition() {
            // Preserve insertion order via LinkedHashMap so reporters see
            // postconditions in the order the contract declared them.
            LinkedHashMap<String, FailureCount> out = new LinkedHashMap<>();
            for (var e : failuresByPostcondition.entrySet()) {
                out.put(e.getKey(), new FailureCount(e.getValue().count, e.getValue().exemplars));
            }
            return Map.copyOf(out);
        }
    }
}
