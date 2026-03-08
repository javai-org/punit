package org.javai.punit.reporting;

/**
 * Receives verdict events produced by probabilistic test executions.
 *
 * <p>Implementations dispatch verdicts to external systems — logging, webhooks,
 * observability platforms, or custom integrations. Multiple sinks can be
 * composed via {@link CompositeVerdictSink}.
 *
 * <p>Implementations must be thread-safe if used from concurrent test execution.
 */
@FunctionalInterface
public interface VerdictSink {

    /**
     * Accepts a verdict event for dispatch.
     *
     * @param event the verdict event to process
     */
    void accept(VerdictEvent event);
}
