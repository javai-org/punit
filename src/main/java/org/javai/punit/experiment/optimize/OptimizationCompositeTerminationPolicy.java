package org.javai.punit.experiment.optimize;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Combines multiple termination policies. Terminates when ANY policy triggers.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Stop after 20 iterations OR if no improvement for 5 iterations
 * OptimizationTerminationPolicy policy = new OptimizationCompositeTerminationPolicy(
 *     new OptimizationMaxIterationsPolicy(20),
 *     new OptimizationNoImprovementPolicy(5)
 * );
 * }</pre>
 */
public final class OptimizationCompositeTerminationPolicy implements OptimizationTerminationPolicy {

    private final List<OptimizationTerminationPolicy> policies;

    /**
     * Creates a composite policy from the given policies.
     *
     * @param policies the policies to combine
     * @throws IllegalArgumentException if no policies provided
     */
    public OptimizationCompositeTerminationPolicy(OptimizationTerminationPolicy... policies) {
        if (policies == null || policies.length == 0) {
            throw new IllegalArgumentException("At least one policy is required");
        }
        this.policies = List.of(policies);
    }

    /**
     * Creates a composite policy from a list of policies.
     *
     * @param policies the policies to combine
     * @throws IllegalArgumentException if list is null or empty
     */
    public OptimizationCompositeTerminationPolicy(List<OptimizationTerminationPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("At least one policy is required");
        }
        this.policies = List.copyOf(policies);
    }

    @Override
    public Optional<OptimizationTerminationReason> shouldTerminate(OptimizationHistory history) {
        for (OptimizationTerminationPolicy policy : policies) {
            Optional<OptimizationTerminationReason> reason = policy.shouldTerminate(history);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    @Override
    public String description() {
        return policies.stream()
                .map(OptimizationTerminationPolicy::description)
                .collect(Collectors.joining(" OR "));
    }
}
