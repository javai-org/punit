/**
 * Baseline-file machinery.
 *
 * <p>This package is the on-disk persistence layer for
 * {@link org.javai.punit.api.spec.BaselineStatistics} values
 * produced by {@code Experiment.measuring(...)} and consumed by the
 * empirical variants of {@link org.javai.punit.engine.criteria.BernoulliPassRate}
 * and {@link org.javai.punit.api.spec.PercentileLatency}.
 *
 * <p>The schema is documented in
 * {@code docs/DES-BASELINE-YAML-SCHEMA.md}.
 */
package org.javai.punit.engine.baseline;
