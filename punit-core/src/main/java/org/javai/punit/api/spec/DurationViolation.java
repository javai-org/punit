package org.javai.punit.api.spec;

import java.time.Duration;
import java.util.Objects;
import org.javai.punit.api.UseCase;

/**
 * Records that a sample exceeded the use case's declared
 * per-sample wall-clock bound. Surfaced on
 * {@link SampleClassification} when the bound is set on the
 * {@link UseCase#maxLatency() use case}
 * and the actual sample duration exceeds it.
 *
 * <p>A violation is an additional facet of a sample, not a verdict
 * on its own — postcondition results and (optional) match status
 * are still collected. The framework's reporters and the verdict
 * machinery decide what to do with violations; the classifier just
 * records them.
 *
 * @param observed the wall-clock duration the sample took
 * @param max the declared maximum the use case permitted
 */
public record DurationViolation(Duration observed, Duration max) {

    public DurationViolation {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(max, "max");
    }
}
