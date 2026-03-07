package org.javai.punit.experiment.optimize;

/**
 * Defines the optimization direction.
 *
 * <p>The objective determines how scores are compared to identify the best iteration:
 * <ul>
 *   <li>{@link #MAXIMIZE}: Higher scores are better (e.g., success rate)</li>
 *   <li>{@link #MINIMIZE}: Lower scores are better (e.g., error rate, latency)</li>
 * </ul>
 */
public enum OptimizationObjective {

    /**
     * Higher scores are better.
     *
     * <p>Use for metrics like success rate, accuracy, throughput.
     */
    MAXIMIZE {
        @Override
        public boolean isBetter(double candidate, double current) {
            return candidate > current;
        }
    },

    /**
     * Lower scores are better.
     *
     * <p>Use for metrics like error rate, latency, cost.
     */
    MINIMIZE {
        @Override
        public boolean isBetter(double candidate, double current) {
            return candidate < current;
        }
    };

    /**
     * Determines if a candidate score is better than the current best.
     *
     * @param candidate the new score to evaluate
     * @param current the current best score
     * @return true if candidate is better according to this objective
     */
    public abstract boolean isBetter(double candidate, double current);
}
