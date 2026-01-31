package org.javai.punit.examples.infrastructure.llm;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import org.javai.outcome.Failure;
import org.javai.outcome.FailureId;
import org.javai.outcome.boundary.FailureClassifier;

/**
 * Classifies HTTP-related exceptions into appropriate failure types for retry logic.
 *
 * <p>This classifier determines which errors are transient (retriable) vs permanent:
 * <ul>
 *   <li><b>Transient:</b> Timeouts, network errors, connection resets</li>
 *   <li><b>Permanent:</b> Other unexpected exceptions</li>
 * </ul>
 *
 * <p>HTTP status code classification is handled separately in the response handling
 * code, not in this classifier (since successful HTTP responses don't throw exceptions).
 *
 * @see org.javai.outcome.retry.Retrier
 */
final class HttpFailureClassifier implements FailureClassifier {

    private static final String NAMESPACE = "llm";

    @Override
    public Failure classify(String operation, Throwable throwable) {
        if (throwable instanceof HttpTimeoutException) {
            return Failure.transientFailure(
                    FailureId.of(NAMESPACE, "timeout"),
                    "Request timed out: " + throwable.getMessage(),
                    operation,
                    throwable
            );
        }

        if (throwable instanceof IOException) {
            return Failure.transientFailure(
                    FailureId.of(NAMESPACE, "network"),
                    "Network error: " + throwable.getMessage(),
                    operation,
                    throwable
            );
        }

        if (throwable instanceof InterruptedException) {
            // Preserve interrupt status and treat as permanent (don't retry)
            Thread.currentThread().interrupt();
            return Failure.permanentFailure(
                    FailureId.of(NAMESPACE, "interrupted"),
                    "Request interrupted",
                    operation,
                    throwable
            );
        }

        // Unknown exceptions are permanent (don't retry)
        return Failure.permanentFailure(
                FailureId.of(NAMESPACE, "unexpected"),
                throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getName(),
                operation,
                throwable
        );
    }
}
