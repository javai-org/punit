package org.javai.punit.experiment.engine;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;

/**
 * An iterator that consumes factor arguments sequentially from a stream.
 *
 * <p>This iterator is used with {@link StreamingHashableFactorSource} (STREAM_SEQUENTIAL type)
 * to provide factor arguments for samples. Each sample consumes the next element from the
 * underlying stream—there is no cycling.
 *
 * <h2>Sequential Behavior</h2>
 * <pre>
 * Stream: [A, B, C, D, E, ...]  (potentially infinite)
 * Samples: 5
 *
 * Sample 0 → A (consumes element 0)
 * Sample 1 → B (consumes element 1)
 * Sample 2 → C (consumes element 2)
 * Sample 3 → D (consumes element 3)
 * Sample 4 → E (consumes element 4)
 * </pre>
 *
 * <h2>Stream Exhaustion</h2>
 * <p>If the stream is exhausted before all samples are consumed, an exception is thrown.
 * This can happen if the stream is finite and has fewer elements than the sample count.
 *
 * <h2>Memory Efficiency</h2>
 * <p>Unlike cycling iteration, sequential consumption does not materialize the entire
 * stream. Memory usage is constant regardless of sample count.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Stream<FactorArguments> factorStream = source.factors();
 * SequentialFactorIterator iterator = new SequentialFactorIterator(factorStream, sampleCount);
 *
 * for (int i = 0; i < sampleCount; i++) {
 *     FactorArguments args = iterator.next();
 *     executeSample(args);
 * }
 * }</pre>
 *
 * @see StreamingHashableFactorSource
 * @see org.javai.punit.api.FactorSourceType#STREAM_SEQUENTIAL
 */
public class SequentialFactorIterator implements Iterator<FactorArguments>, AutoCloseable {

    private final Iterator<FactorArguments> streamIterator;
    private final int totalSamples;
    private int currentIndex;
    private boolean closed;

    /**
     * Creates a new SequentialFactorIterator.
     *
     * @param factorStream the stream of factor arguments to consume
     * @param totalSamples the total number of samples to produce
     */
    public SequentialFactorIterator(Stream<FactorArguments> factorStream, int totalSamples) {
        Objects.requireNonNull(factorStream, "factorStream must not be null");
        if (totalSamples < 0) {
            throw new IllegalArgumentException("totalSamples must be non-negative");
        }

        this.streamIterator = factorStream.iterator();
        this.totalSamples = totalSamples;
        this.currentIndex = 0;
        this.closed = false;
    }

    @Override
    public boolean hasNext() {
        return !closed && currentIndex < totalSamples;
    }

    @Override
    public FactorArguments next() {
        if (closed) {
            throw new IllegalStateException("Iterator has been closed");
        }
        if (currentIndex >= totalSamples) {
            throw new NoSuchElementException("All samples have been consumed");
        }

        if (!streamIterator.hasNext()) {
            throw new StreamExhaustedException(
                    "Factor stream exhausted after " + currentIndex + " elements, " +
                            "but " + totalSamples + " samples were requested. " +
                            "Either provide more factors or reduce the sample count.");
        }

        FactorArguments args = streamIterator.next();
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
     * Returns the number of remaining samples.
     *
     * @return the remaining sample count
     */
    public int getRemainingSamples() {
        return totalSamples - currentIndex;
    }

    @Override
    public void close() {
        closed = true;
    }

    /**
     * Exception thrown when the factor stream is exhausted before all samples are consumed.
     */
    public static class StreamExhaustedException extends RuntimeException {
        public StreamExhaustedException(String message) {
            super(message);
        }
    }
}

