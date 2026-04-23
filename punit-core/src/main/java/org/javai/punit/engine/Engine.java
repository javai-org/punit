package org.javai.punit.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.javai.punit.api.typed.MatchResult;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.ValueMatcher;
import org.javai.punit.api.typed.spec.Configuration;
import org.javai.punit.api.typed.spec.EngineOutcome;
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
 * <p>When a configuration carries expected values (the author built the
 * spec with {@code .expectations(...)}), the engine runs the spec's
 * matcher against each sample's outcome before aggregation, attaching
 * a {@link MatchResult} to the outcome. A failing match turns the
 * outcome into a failed sample via
 * {@link UseCaseOutcome#value() UseCaseOutcome.value()}.
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
            SampleSummary<OT> summary = runConfig(uc, cfg, cycleStart, spec.matcher());
            spec.consume(cfg, summary);
            cycleStart = (cycleStart + cfg.samples()) % cfg.inputs().size();
        }
        return spec.conclude();
    }

    private <FT, IT, OT> SampleSummary<OT> runConfig(
            UseCase<FT, IT, OT> useCase,
            Configuration<FT, IT, OT> cfg,
            int cycleStart,
            Optional<ValueMatcher<OT>> matcher) {

        Aggregator<OT> agg = new Aggregator<>(cfg.expected(), cycleStart, matcher);
        long t0 = System.nanoTime();
        executor.runSamples(useCase, cfg.inputs(), cfg.samples(), cycleStart, agg);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
        return agg.toSummary(elapsed);
    }

    /**
     * Aggregates per-sample outcomes and, when the configuration
     * carries expected values and the spec supplies a matcher, attaches
     * a {@link MatchResult} to each outcome before recording it.
     */
    private static final class Aggregator<OT> implements SampleObserver<OT> {
        private final List<OT> expected;
        private final int cycleStart;
        private final Optional<ValueMatcher<OT>> matcher;
        private final List<UseCaseOutcome<OT>> outcomes = new ArrayList<>();

        Aggregator(List<OT> expected, int cycleStart, Optional<ValueMatcher<OT>> matcher) {
            this.expected = expected;
            this.cycleStart = cycleStart;
            this.matcher = matcher;
        }

        @Override public void onSample(int index, UseCaseOutcome<OT> outcome, Duration elapsed) {
            outcomes.add(maybeAttachMatch(index, outcome));
        }

        private UseCaseOutcome<OT> maybeAttachMatch(int index, UseCaseOutcome<OT> outcome) {
            if (expected.isEmpty() || matcher.isEmpty()) {
                return outcome;
            }
            // index comes from executor-relative sample number; cycleStart
            // composes the round-robin position into the configuration's
            // input/expected list, matching the executor's own cycling.
            int cycleIndex = (cycleStart + index) % expected.size();
            OT expectedValue = expected.get(cycleIndex);
            OT actualValue = outcome.rawResult();
            MatchResult result = matcher.get().match(expectedValue, actualValue);
            return outcome.withMatch(result);
        }

        SampleSummary<OT> toSummary(Duration elapsed) {
            return SampleSummary.from(outcomes, elapsed);
        }
    }
}
