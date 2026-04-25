package org.javai.punit.api.typed.spec;

import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.ValueMatcher;

/**
 * Strategy contract the engine dispatches through.
 *
 * <p>Per {@code DES-SPEC-AS-STRATEGY.md}:
 * <ul>
 *   <li>{@link #configurations()} yields a finite, reproducible
 *       iterator of run-units.</li>
 *   <li>{@link #useCaseFactory()} supplies the constructor the engine
 *       invokes once per configuration.</li>
 *   <li>{@link #matcher()} supplies the instance-conformance
 *       matcher; present when the spec was built with
 *       {@code .expectations(...)}, empty otherwise.</li>
 *   <li>{@link #consume(Configuration, SampleSummary) consume(cfg, summary)}
 *       receives the per-configuration aggregate; the spec accumulates
 *       what it needs for {@link #conclude()}.</li>
 *   <li>{@link #conclude()} produces the
 *       {@link EngineResult} — a verdict for a probabilistic test,
 *       an artefact description for an experiment.</li>
 * </ul>
 *
 * <p>The engine must not branch on the concrete subtype. The sealed
 * {@code permits} clause exists for tool-side exhaustive matching
 * (reporters, doc generators) — the engine itself treats the spec
 * polymorphically.
 *
 * @param <FT> the factor record type
 * @param <IT> the per-sample input type
 * @param <OT> the per-sample outcome value type
 */
public sealed interface DataGenerationSpec<FT, IT, OT>
        permits MeasureSpec, ExploreSpec, OptimizeSpec, ProbabilisticTestSpec {

    Iterator<Configuration<FT, IT, OT>> configurations();

    Function<FT, UseCase<FT, IT, OT>> useCaseFactory();

    /**
     * @return the spec's instance-conformance matcher, when the spec
     *         was built with {@code .expectations(...)}; empty
     *         otherwise. The engine consults this only when the
     *         current {@link Configuration} has non-empty
     *         {@code expected} values.
     */
    default Optional<ValueMatcher<OT>> matcher() {
        return Optional.empty();
    }

    void consume(Configuration<FT, IT, OT> config, SampleSummary<OT> summary);

    EngineResult conclude();

    // ── Stage-3 resource-control / latency accessors ────────────────

    /**
     * Wall-clock budget the engine honours when sampling this spec.
     * Empty = no limit.
     */
    default Optional<Duration> timeBudget() { return Optional.empty(); }

    /** Token budget the engine honours when sampling this spec. Empty = no limit. */
    default OptionalLong tokenBudget() { return OptionalLong.empty(); }

    /**
     * Static per-sample token charge added to the token tally in
     * addition to whatever the use case reports via
     * {@link org.javai.punit.api.typed.UseCaseOutcome#tokens()}.
     */
    default long tokenCharge() { return 0L; }

    /** What the engine does when a budget exhausts early. */
    default BudgetExhaustionPolicy budgetPolicy() {
        return BudgetExhaustionPolicy.FAIL;
    }

    /**
     * How the engine treats a thrown exception from
     * {@link UseCase#apply(Object)}.
     */
    default ExceptionPolicy exceptionPolicy() {
        return ExceptionPolicy.ABORT_TEST;
    }

    /**
     * Upper bound on the number of detailed failing outcomes retained
     * for diagnostics. Counting is never affected; only example
     * retention is capped.
     */
    default int maxExampleFailures() { return 10; }
}
