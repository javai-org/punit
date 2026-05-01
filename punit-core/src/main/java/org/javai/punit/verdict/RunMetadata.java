package org.javai.punit.verdict;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.punit.api.spec.ProbabilisticTestResult;

/**
 * Per-run metadata that the typed pipeline doesn't carry on its
 * result but the RP07 verdict XML requires — test identity, optional
 * correlation ID, and a bag of environment metadata.
 *
 * <p>Built at the JUnit-extension boundary (where {@code className} and
 * {@code methodName} are knowable via stack walk or the test extension
 * context) and threaded into {@link VerdictAdapter} alongside the
 * {@link ProbabilisticTestResult result}
 * itself. The result captures what happened in the run; this metadata
 * captures who ran it and where.
 *
 * <p>{@link #correlationId()} and {@link #environmentMetadata()} are
 * optional. The adapter generates a UUID-fragment correlation ID when
 * absent (matching the legacy verdict builder's behaviour) and treats
 * empty environment metadata as the absence of an {@code <environment>}
 * element rather than an empty one.
 *
 * @param className           the test class's fully-qualified name;
 *                            non-blank
 * @param methodName          the test method name; non-blank
 * @param useCaseId           the use-case identifier, when known; surfaces
 *                            on the verdict's {@code <identity use-case-id>}
 *                            attribute and falls back to {@code className}
 *                            when absent (mirroring the legacy pipeline's
 *                            behaviour)
 * @param correlationId       opaque correlation ID for distributed
 *                            tracing; absent → adapter generates one
 * @param environmentMetadata free-form key/value pairs surfaced as
 *                            {@code <environment>} entries; empty →
 *                            adapter omits the section
 */
public record RunMetadata(
        String className,
        String methodName,
        Optional<String> useCaseId,
        Optional<String> correlationId,
        Map<String, String> environmentMetadata) {

    public RunMetadata {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(useCaseId, "useCaseId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(environmentMetadata, "environmentMetadata");
        if (className.isBlank()) {
            throw new IllegalArgumentException("className must not be blank");
        }
        if (methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        environmentMetadata = Map.copyOf(environmentMetadata);
    }

    /**
     * Convenience for the common case of class + method only —
     * defaults useCaseId, correlationId, and environmentMetadata to
     * empty.
     */
    public static RunMetadata of(String className, String methodName) {
        return new RunMetadata(
                className, methodName,
                Optional.empty(), Optional.empty(), Map.of());
    }

    /**
     * Convenience adding a use-case identifier; correlationId and
     * environmentMetadata default to empty.
     */
    public static RunMetadata of(String className, String methodName, String useCaseId) {
        Optional<String> wrapped = (useCaseId == null || useCaseId.isBlank())
                ? Optional.empty()
                : Optional.of(useCaseId);
        return new RunMetadata(
                className, methodName, wrapped, Optional.empty(), Map.of());
    }
}
