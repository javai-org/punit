package org.javai.punit.api.typed.spec;

import java.util.Iterator;
import java.util.function.Function;

import org.javai.punit.api.typed.UseCase;

/**
 * Strategy contract the engine dispatches through.
 *
 * <p>Per {@code DES-SPEC-AS-STRATEGY.md}:
 * <ul>
 *   <li>{@link #configurations()} yields a finite, reproducible
 *       iterator of run-units.</li>
 *   <li>{@link #useCaseFactory()} supplies the constructor the engine
 *       invokes once per configuration.</li>
 *   <li>{@link #consume(Configuration, SampleSummary) consume(cfg, summary)}
 *       receives the per-configuration aggregate; the spec accumulates
 *       what it needs for {@link #conclude()}.</li>
 *   <li>{@link #conclude()} produces the
 *       {@link EngineOutcome} — a verdict for a probabilistic test,
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
public sealed interface Spec<FT, IT, OT>
        permits MeasureSpec, ExploreSpec, OptimizeSpec, ProbabilisticTestSpec {

    /**
     * @return a finite, reproducible iterator of run-units. The
     *         iterator may be stateful (optimize adapts based on
     *         {@code consume()} history) but is bounded — a fresh
     *         iterator on the same spec always yields the same
     *         sequence up to spec-level non-determinism (e.g. an
     *         optimizer whose mutator is deterministic produces
     *         identical sequences on every run).
     */
    Iterator<Configuration<FT, IT>> configurations();

    /**
     * @return the use case factory. The engine calls this once per
     *         configuration to produce the {@link UseCase} instance
     *         used for sampling.
     */
    Function<FT, UseCase<FT, IT, OT>> useCaseFactory();

    /**
     * Reports the aggregated observations for one configuration. The
     * spec stores whatever it needs to produce a verdict or artefact
     * in {@link #conclude()}.
     *
     * @param config the configuration that was just sampled
     * @param summary the aggregate
     */
    void consume(Configuration<FT, IT> config, SampleSummary<OT> summary);

    /**
     * Produces the spec's final result after the last configuration
     * has been consumed.
     *
     * <p>Named {@code conclude} rather than {@code finalize} because
     * {@code Object.finalize()} (deprecated-for-removal) forces
     * {@code void} return type on any override.
     *
     * @return the engine outcome (verdict or artefact)
     */
    EngineOutcome conclude();
}
