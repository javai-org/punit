package org.javai.punit.api.typed.spec;

/**
 * Reduces a {@link SampleSummary} to a single comparable score for an
 * {@link OptimizeSpec} iteration.
 */
@FunctionalInterface
public interface Scorer {
    double score(SampleSummary<?> summary);
}
