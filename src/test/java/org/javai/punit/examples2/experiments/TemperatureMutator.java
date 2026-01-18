package org.javai.punit.examples2.experiments;

import org.javai.punit.experiment.optimize.FactorMutator;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizationRecord;
import org.javai.punit.experiment.optimize.OptimizeHistory;

import java.util.List;

/**
 * Mutator for numeric temperature parameter optimization.
 *
 * <p>This mutator implements a simple gradient-like search strategy for
 * finding optimal temperature values. It adjusts the temperature based
 * on the direction that has shown improvement in recent iterations.
 *
 * <h2>Strategy</h2>
 * <ul>
 *   <li>Initial step size: 0.1</li>
 *   <li>Direction: Determined by comparing recent scores</li>
 *   <li>Bounds: 0.0 to 1.0 (valid temperature range)</li>
 *   <li>Adaptive: Step size reduces when oscillating</li>
 * </ul>
 *
 * @see org.javai.punit.experiment.optimize.FactorMutator
 */
public class TemperatureMutator implements FactorMutator<Double> {

    private static final double MIN_TEMP = 0.0;
    private static final double MAX_TEMP = 1.0;
    private static final double INITIAL_STEP = 0.1;
    private static final double MIN_STEP = 0.01;

    private double stepSize = INITIAL_STEP;
    private int direction = -1; // Start by decreasing (lower temp = more reliable)

    @Override
    public Double mutate(Double currentValue, OptimizeHistory history) throws MutationException {
        if (currentValue == null) {
            return 0.5; // Default starting point
        }

        // Analyze history to determine direction
        adjustDirectionFromHistory(history);

        // Calculate new value
        double newValue = currentValue + (direction * stepSize);

        // Clamp to valid range
        newValue = Math.max(MIN_TEMP, Math.min(MAX_TEMP, newValue));

        // If we hit a boundary, reverse direction and reduce step
        if (newValue == MIN_TEMP || newValue == MAX_TEMP) {
            direction = -direction;
            stepSize = Math.max(MIN_STEP, stepSize * 0.5);
        }

        return newValue;
    }

    /**
     * Analyzes optimization history to determine the best direction.
     */
    private void adjustDirectionFromHistory(OptimizeHistory history) {
        List<OptimizationRecord> iterations = history.iterations();

        if (iterations.size() < 2) {
            return; // Not enough data
        }

        // Get the last two successful iterations
        List<OptimizationRecord> successful = history.successfulIterations();
        if (successful.size() < 2) {
            return;
        }

        OptimizationRecord prev = successful.get(successful.size() - 2);
        OptimizationRecord last = successful.get(successful.size() - 1);

        // Get temperature values
        Double prevTemp = prev.aggregate().treatmentFactorValue();
        Double lastTemp = last.aggregate().treatmentFactorValue();

        // Determine if we improved
        boolean improved = last.score() > prev.score();

        // Determine the direction we moved
        int movedDirection = Double.compare(lastTemp, prevTemp);

        if (improved) {
            // Keep going in the same direction
            direction = movedDirection != 0 ? movedDirection : direction;
        } else {
            // Reverse direction and reduce step size
            direction = -direction;
            stepSize = Math.max(MIN_STEP, stepSize * 0.7);
        }
    }

    @Override
    public String description() {
        return "Gradient-like temperature optimization with adaptive step size";
    }

    @Override
    public void validate(Double value) throws MutationException {
        if (value == null) {
            throw new MutationException("Temperature cannot be null");
        }
        if (value < MIN_TEMP || value > MAX_TEMP) {
            throw new MutationException(
                    "Temperature must be between " + MIN_TEMP + " and " + MAX_TEMP + ", got: " + value);
        }
    }
}
