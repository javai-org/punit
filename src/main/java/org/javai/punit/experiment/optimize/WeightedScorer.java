package org.javai.punit.experiment.optimize;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Combines multiple scorers with configurable weights.
 *
 * <p>Use when optimizing for multiple objectives simultaneously.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // 70% success rate + 30% cost efficiency
 * Scorer<OptimizationIterationAggregate> scorer = new WeightedScorer(
 *     new WeightedScorer.WeightedComponent(new SuccessRateScorer(), 0.7),
 *     new WeightedScorer.WeightedComponent(new CostEfficiencyScorer(), 0.3)
 * );
 * }</pre>
 */
public final class WeightedScorer implements Scorer<OptimizationIterationAggregate> {

    private final List<WeightedComponent> components;

    /**
     * Creates a WeightedScorer with the given components.
     *
     * @param components the weighted scorer components
     */
    public WeightedScorer(WeightedComponent... components) {
        if (components == null || components.length == 0) {
            throw new IllegalArgumentException("At least one component is required");
        }
        this.components = List.of(components);
    }

    /**
     * Creates a WeightedScorer from a list of components.
     *
     * @param components the weighted scorer components
     */
    public WeightedScorer(List<WeightedComponent> components) {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("At least one component is required");
        }
        this.components = List.copyOf(components);
    }

    @Override
    public double score(OptimizationIterationAggregate aggregate) throws ScoringException {
        double totalScore = 0.0;
        double totalWeight = 0.0;

        for (WeightedComponent c : components) {
            totalScore += c.scorer().score(aggregate) * c.weight();
            totalWeight += c.weight();
        }

        return totalWeight > 0 ? totalScore / totalWeight : 0.0;
    }

    @Override
    public String description() {
        return components.stream()
                .map(c -> String.format("%.0f%% %s", c.weight() * 100, c.scorer().description()))
                .collect(Collectors.joining(" + "));
    }

    /**
     * A scorer with its weight in the combination.
     *
     * @param scorer the scorer to apply
     * @param weight the weight (0.0 to 1.0 recommended, but any positive value works)
     */
    public record WeightedComponent(
            Scorer<OptimizationIterationAggregate> scorer,
            double weight
    ) {
        /**
         * Creates a WeightedComponent with validation.
         */
        public WeightedComponent {
            if (scorer == null) {
                throw new IllegalArgumentException("scorer must not be null");
            }
            if (weight < 0) {
                throw new IllegalArgumentException("weight must be non-negative");
            }
        }
    }
}
