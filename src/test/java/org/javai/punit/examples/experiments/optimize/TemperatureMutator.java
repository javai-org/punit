package org.javai.punit.examples.experiments.optimize;

import org.javai.punit.experiment.optimize.FactorMutator;
import org.javai.punit.experiment.optimize.MutationException;
import org.javai.punit.experiment.optimize.OptimizeHistory;

/**
 * Mutator for temperature parameter that decreases linearly from 1.0 to 0.0.
 *
 * <p>This mutator demonstrates a simple, predictable optimization strategy
 * for the temperature parameter. It starts at the maximum temperature (1.0)
 * and decreases by a fixed step each iteration.
 *
 * <h2>Strategy</h2>
 * <ul>
 *   <li>Initial value: 1.0 (maximum creativity/randomness)</li>
 *   <li>Step: -0.1 per iteration</li>
 *   <li>Final value: 0.0 (maximum determinism)</li>
 * </ul>
 *
 * <h2>Expected Outcome</h2>
 * <p>For structured JSON output tasks like the shopping basket use case,
 * lower temperatures should produce higher success rates because:
 * <ul>
 *   <li>High temperature (1.0): More "creative" responses that deviate from format</li>
 *   <li>Low temperature (0.0): Deterministic responses that follow instructions</li>
 * </ul>
 *
 * <p>The optimization output should show a clear trend of improving scores
 * as temperature decreases, demonstrating that for structured output tasks,
 * lower temperatures are more reliable.
 *
 * @see org.javai.punit.experiment.optimize.FactorMutator
 */
public class TemperatureMutator implements FactorMutator<Double> {

    private static final double MIN_TEMP = 0.0;
    private static final double MAX_TEMP = 1.0;
    private static final double STEP = 0.1;

    @Override
    public Double mutate(Double currentValue, OptimizeHistory history) throws MutationException {
        if (currentValue == null) {
            // Start at maximum temperature (naive starting point)
            return MAX_TEMP;
        }

        // Simple linear decrease
        double newValue = currentValue - STEP;

        // Clamp to valid range
        return Math.max(MIN_TEMP, newValue);
    }

    @Override
    public String description() {
        return "Linear temperature decrease from 1.0 to 0.0 in steps of 0.1";
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
