package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

/**
 * One run-unit as surfaced by {@link Spec#configurations()}: the factor
 * values to instantiate the use case with, the inputs to cycle through,
 * and how many samples to execute.
 *
 * @param factors the factor record (immutable)
 * @param inputs the per-sample input values; the engine walks them
 *               round-robin for {@code samples} iterations
 * @param samples the number of counted samples to execute
 * @param <FT> the factor record type
 * @param <IT> the input type
 */
public record Configuration<FT, IT>(FT factors, List<IT> inputs, int samples) {

    public Configuration {
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must be non-empty");
        }
        if (samples < 0) {
            throw new IllegalArgumentException("samples must be non-negative");
        }
        inputs = List.copyOf(inputs);
    }
}
