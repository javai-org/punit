package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

/**
 * One run-unit as surfaced by {@link DataGenerationSpec#configurations()}: the factor
 * values to instantiate the use case with, the inputs to cycle through,
 * optionally a parallel list of expected values (instance-conformance
 * checking), and how many samples to execute.
 *
 * <p>{@code expected} is either empty (contract-only path) or of the
 * same length as {@code inputs} (instance-conformance path). When
 * non-empty, the engine runs the spec's matcher on each sample and
 * attaches a {@link org.javai.punit.api.typed.MatchResult} to the
 * sample's outcome before handing it to the observer.
 *
 * @param factors the factor record (immutable)
 * @param inputs the per-sample input values; the engine walks them
 *               round-robin for {@code samples} iterations
 * @param expected expected values paired by index with {@code inputs};
 *                 empty when no instance-conformance check is configured
 * @param samples the number of counted samples to execute
 * @param <FT> the factor record type
 * @param <IT> the input type
 * @param <OT> the outcome value type (also the expected-value type)
 */
public record Configuration<FT, IT, OT>(
        FT factors,
        List<IT> inputs,
        List<OT> expected,
        int samples) {

    public Configuration {
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(expected, "expected");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must be non-empty");
        }
        if (!expected.isEmpty() && expected.size() != inputs.size()) {
            throw new IllegalArgumentException(
                    "expected (" + expected.size() + ") and inputs (" + inputs.size()
                            + ") must be the same length when expected is non-empty");
        }
        if (samples < 0) {
            throw new IllegalArgumentException("samples must be non-negative");
        }
        inputs = List.copyOf(inputs);
        expected = List.copyOf(expected);
    }

    /** Convenience: build a configuration with no instance-conformance check. */
    public static <FT, IT, OT> Configuration<FT, IT, OT> of(FT factors, List<IT> inputs, int samples) {
        return new Configuration<>(factors, inputs, List.of(), samples);
    }

    public boolean hasExpectations() {
        return !expected.isEmpty();
    }
}
