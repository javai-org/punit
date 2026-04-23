package org.javai.punit.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.Configuration;
import org.javai.punit.api.typed.spec.EngineOutcome;
import org.javai.punit.api.typed.spec.SampleExecutor;
import org.javai.punit.api.typed.spec.SampleObserver;
import org.javai.punit.api.typed.spec.SampleSummary;
import org.javai.punit.api.typed.spec.Spec;

/**
 * The one engine, shared by every spec flavour.
 *
 * <p>Per {@code DES-SPEC-AS-STRATEGY.md} §7: the engine iterates
 * {@link Spec#configurations() spec.configurations()}, samples each
 * configuration through a {@link SampleExecutor}, hands the resulting
 * {@link SampleSummary} to
 * {@link Spec#consume(Configuration, SampleSummary) spec.consume(...)},
 * and finally invokes {@link Spec#conclude() spec.conclude()} to
 * obtain the run's {@link EngineOutcome}.
 *
 * <p>No {@code instanceof} / {@code switch} on spec subtype anywhere
 * in this class or its helpers. The architecture test in
 * {@code punit-core/src/test/java/.../architecture/TypedEngineArchitectureTest}
 * forbids it.
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
        Iterator<Configuration<FT, IT>> it = spec.configurations();
        int cycleStart = 0;
        while (it.hasNext()) {
            Configuration<FT, IT> cfg = it.next();
            UseCase<FT, IT, OT> uc = spec.useCaseFactory().apply(cfg.factors());
            SampleSummary<OT> summary = runConfig(uc, cfg, cycleStart);
            spec.consume(cfg, summary);
            cycleStart = (cycleStart + cfg.samples()) % cfg.inputs().size();
        }
        return spec.conclude();
    }

    private <FT, IT, OT> SampleSummary<OT> runConfig(
            UseCase<FT, IT, OT> useCase,
            Configuration<FT, IT> cfg,
            int cycleStart) {

        Aggregator<OT> agg = new Aggregator<>();
        long t0 = System.nanoTime();
        executor.runSamples(useCase, cfg.inputs(), cfg.samples(), cycleStart, agg);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
        return agg.toSummary(elapsed);
    }

    private static final class Aggregator<OT> implements SampleObserver<OT> {
        final List<UseCaseOutcome<OT>> outcomes = new ArrayList<>();

        @Override public void onSample(int index, UseCaseOutcome<OT> outcome, Duration elapsed) {
            outcomes.add(outcome);
        }

        SampleSummary<OT> toSummary(Duration elapsed) {
            return new SampleSummary<>(outcomes, elapsed);
        }
    }
}
