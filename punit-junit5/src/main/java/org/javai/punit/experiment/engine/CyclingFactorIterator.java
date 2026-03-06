package org.javai.punit.experiment.engine;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.javai.punit.api.FactorArguments;

/**
 * An iterator that cycles through a list of factor arguments.
 *
 * <p>This iterator is used with {@link DefaultHashableFactorSource} (LIST_CYCLING type)
 * to provide factor arguments for samples. When the list is exhausted, the iterator
 * cycles back to the beginning.
 *
 * <h2>Cycling Behavior</h2>
 * <pre>
 * List: [A, B, C]  (3 elements)
 * Samples: 7
 *
 * Sample 0 → A (index 0 % 3 = 0)
 * Sample 1 → B (index 1 % 3 = 1)
 * Sample 2 → C (index 2 % 3 = 2)
 * Sample 3 → A (index 3 % 3 = 0)  ← cycle restarts
 * Sample 4 → B (index 4 % 3 = 1)
 * Sample 5 → C (index 5 % 3 = 2)
 * Sample 6 → A (index 6 % 3 = 0)
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * List<FactorArguments> factors = source.factors().toList();
 * CyclingFactorIterator iterator = new CyclingFactorIterator(factors, sampleCount);
 *
 * for (int i = 0; i < sampleCount; i++) {
 *     FactorArguments args = iterator.next();
 *     executeSample(args);
 * }
 * }</pre>
 *
 * @see DefaultHashableFactorSource
 * @see org.javai.punit.api.FactorSourceType#LIST_CYCLING
 */
public class CyclingFactorIterator implements Iterator<FactorArguments> {

    private final List<FactorArguments> factors;
    private final int totalSamples;
    private int currentIndex;

    /**
     * Creates a new CyclingFactorIterator.
     *
     * @param factors      the list of factor arguments to cycle through
     * @param totalSamples the total number of samples to produce
     * @throws IllegalArgumentException if factors is empty
     */
    public CyclingFactorIterator(List<FactorArguments> factors, int totalSamples) {
        Objects.requireNonNull(factors, "factors must not be null");
        if (factors.isEmpty()) {
            throw new IllegalArgumentException("factors must not be empty");
        }
        if (totalSamples < 0) {
            throw new IllegalArgumentException("totalSamples must be non-negative");
        }

        this.factors = factors;
        this.totalSamples = totalSamples;
        this.currentIndex = 0;
    }

    @Override
    public boolean hasNext() {
        return currentIndex < totalSamples;
    }

    @Override
    public FactorArguments next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more samples available");
        }

        FactorArguments args = factors.get(currentIndex % factors.size());
        currentIndex++;
        return args;
    }

    /**
     * Returns the current sample index (0-based).
     *
     * @return the current index
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Returns the total number of samples this iterator will produce.
     *
     * @return the total sample count
     */
    public int getTotalSamples() {
        return totalSamples;
    }

    /**
     * Returns the number of unique factors in the underlying list.
     *
     * @return the factor count
     */
    public int getFactorCount() {
        return factors.size();
    }

    /**
     * Returns how many times each factor will be used (approximately).
     *
     * <p>Due to cycling, some factors may be used one more time than others
     * if {@code totalSamples % factorCount != 0}.
     *
     * @return the approximate number of times each factor is used
     */
    public int getApproximateUsagePerFactor() {
        return (totalSamples + factors.size() - 1) / factors.size();
    }

    /**
     * Resets the iterator to the beginning.
     */
    public void reset() {
        this.currentIndex = 0;
    }
}

