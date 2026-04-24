package org.javai.punit.api.typed.spec;

/**
 * Sentinel for criteria that read no baseline at all. A criterion
 * that declares {@code NoStatistics} as its statistics kind never
 * consults {@link EvaluationContext#baseline()} — the framework
 * therefore treats its resolution as always-empty and never routes
 * any persisted baseline slice to it.
 */
public enum NoStatistics implements BaselineStatistics {
    INSTANCE
}
