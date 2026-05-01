/**
 * Spec builders and the strategy contract the engine dispatches
 * through.
 *
 * <p>Each concrete spec ({@link org.javai.punit.api.spec.Experiment},
 * {@link org.javai.punit.api.spec.Experiment},
 * {@link org.javai.punit.api.spec.Experiment},
 * {@link org.javai.punit.api.spec.ProbabilisticTest}) implements
 * {@link org.javai.punit.api.spec.Spec}. The engine iterates
 * {@link org.javai.punit.api.spec.Spec#configurations()},
 * samples each configuration through a
 * {@link org.javai.punit.api.spec.SampleExecutor}, hands the
 * resulting {@link org.javai.punit.api.spec.SampleSummary} back to
 * {@link org.javai.punit.api.spec.Spec#consume(Configuration, SampleSummary)
 * spec.consume(...)}, and finishes by invoking
 * {@link org.javai.punit.api.spec.Spec#conclude() spec.conclude()}
 * — which yields a {@link org.javai.punit.api.spec.EngineResult}.
 *
 * <p>The engine never inspects the concrete spec subtype. All
 * flavour-specific behaviour reaches the engine through the strategy
 * methods on {@code Spec}.
 */
package org.javai.punit.api.spec;
