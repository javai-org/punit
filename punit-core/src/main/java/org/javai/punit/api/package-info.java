/**
 * PUnit public API — annotations, foundational value types, and
 * supporting types for the authoring surface.
 *
 * <h2>Annotations</h2>
 * <ul>
 *   <li>{@link org.javai.punit.api.ProbabilisticTest} — marks a method
 *       as a probabilistic test. Method body builds a spec via
 *       {@code PUnit.testing(...)} and asserts via
 *       {@code .assertPasses()}.</li>
 *   <li>{@link org.javai.punit.api.Experiment} — marks a method as an
 *       experiment (measure / explore / optimize). Method body builds
 *       an experiment spec via {@code PUnit.measuring(...)} /
 *       {@code .exploring(...)} / {@code .optimizing(...)} and runs
 *       it via {@code .run()}.</li>
 * </ul>
 *
 * <h2>Foundational types</h2>
 * <ul>
 *   <li>{@link org.javai.punit.api.UseCase} — the interface every
 *       use case implements; carries the service call, the
 *       postcondition contract, and metadata.</li>
 *   <li>{@link org.javai.punit.api.UseCaseOutcome} — the per-sample
 *       artefact the framework assembles, carrying the result, the
 *       contract, postcondition results, optional match, tokens,
 *       and duration.</li>
 *   <li>{@link org.javai.punit.api.FactorBundle} /
 *       {@link org.javai.punit.api.FactorValue} — bind a factor record
 *       ({@code FT}) to a canonical, content-addressable
 *       representation used by baseline-spec filenames and the YAML
 *       factor block.</li>
 * </ul>
 *
 * <h2>Supporting types</h2>
 * <ul>
 *   <li>{@link org.javai.punit.api.TestIntent} — VERIFICATION (default,
 *       evidential) vs SMOKE (sentinel, non-evidential).</li>
 *   <li>{@link org.javai.punit.api.ThresholdOrigin} — provenance of a
 *       criterion's threshold (SLA, SLO, POLICY, EMPIRICAL, …).</li>
 *   <li>{@link org.javai.punit.api.CovariateCategory} — covariate
 *       classification used by baseline matching.</li>
 *   <li>{@link org.javai.punit.api.BudgetExhaustedBehavior},
 *       {@link org.javai.punit.api.ExceptionHandling},
 *       {@link org.javai.punit.api.TokenChargeRecorder} — supporting
 *       enums and interfaces consumed by the spec builders.</li>
 * </ul>
 */
package org.javai.punit.api;
