package org.javai.punit.api.typed.spec;

/**
 * Marker for a kind of baseline statistics a criterion may read.
 *
 * <p>Unsealed on purpose: a future criterion kind (collision
 * probability, expected-value, distributional fit, …) adds a new
 * {@link BaselineStatistics} implementation without touching existing
 * types or any existing criterion. See
 * {@code docs/DES-CRITERION-EXTENSIBILITY.md} for the rationale.
 */
public interface BaselineStatistics {
}
