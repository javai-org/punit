/**
 * PUnit public API — annotations and supporting types for the typed
 * authoring surface.
 *
 * <h2>Annotations</h2>
 * <ul>
 *   <li>{@link org.javai.punit.api.ProbabilisticTest} — marks a method
 *       as a probabilistic test. Method body builds a typed spec via
 *       {@code PUnit.testing(...)} and asserts via
 *       {@code .assertPasses()}.</li>
 *   <li>{@link org.javai.punit.api.Experiment} — marks a method as an
 *       experiment (measure / explore / optimize). Method body builds
 *       a typed experiment spec via {@code PUnit.measuring(...)} /
 *       {@code .exploring(...)} / {@code .optimizing(...)} and runs
 *       it via {@code .run()}.</li>
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
 *       enums and interfaces consumed by typed builders.</li>
 * </ul>
 */
package org.javai.punit.api;
