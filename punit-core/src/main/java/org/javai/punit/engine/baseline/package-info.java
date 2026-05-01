/**
 * Baseline-file machinery for the typed-compositional pipeline.
 *
 * <p>This package is the on-disk persistence layer for
 * {@link org.javai.punit.api.spec.BaselineStatistics} values
 * produced by {@code Experiment.measuring(...)} and consumed by the
 * empirical variants of {@link org.javai.punit.engine.criteria.BernoulliPassRate}
 * and {@link org.javai.punit.api.spec.PercentileLatency}.
 *
 * <p>The schema is documented in
 * {@code docs/DES-BASELINE-YAML-SCHEMA.md}; the directive driving
 * Stage-4 implementation is {@code DIR-PUNIT-S4-BASELINE-RESOLVER.md}.
 *
 * <p>Distinct from the legacy {@code org.javai.punit.spec.baseline}
 * package, which carries pre-Stage-3.5 baseline machinery (covariate
 * matching, multi-baseline selection, expiration policy) tied to the
 * legacy annotation-driven surface.
 */
package org.javai.punit.engine.baseline;
