package org.javai.punit.reporting;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable verdict produced by a probabilistic test execution.
 *
 * <p>Contains all the information needed for external dispatch: the statistical
 * outcome, structured report data, environment context, and a correlation ID
 * for cross-referencing between CI reports and observability systems.
 *
 * <p>Both the JUnit extension layer ({@code punit-junit5}) and the Sentinel
 * runtime ({@code punit-sentinel}) produce {@code VerdictEvent} instances.
 * The events are dispatched to {@link VerdictSink} implementations.
 *
 * @param correlationId a short, unique, grep-able token (e.g., {@code v:a3f8c2})
 *                      for cross-referencing between JUnit reports and observability systems
 * @param testName the probabilistic test method identifier
 * @param useCaseId the use case being exercised
 * @param passed the composite verdict (conjunctive across dimensions)
 * @param reportEntries structured data from the verdict (pass rate, threshold, etc.)
 * @param environmentMetadata environment context (populated by Sentinel, empty in JUnit context)
 * @param timestamp when the verdict was produced
 */
public record VerdictEvent(
        String correlationId,
        String testName,
        String useCaseId,
        boolean passed,
        Map<String, String> reportEntries,
        Map<String, String> environmentMetadata,
        Instant timestamp
) {

    /**
     * Generates a new correlation ID.
     *
     * <p>The format is {@code v:} followed by 6 hex characters derived from a UUID.
     * This is short enough to be human-readable in logs and CI dashboards, yet
     * unique enough for practical cross-referencing.
     *
     * @return a new correlation ID, e.g., {@code v:a3f8c2}
     */
    public static String newCorrelationId() {
        return "v:" + UUID.randomUUID().toString().substring(0, 6);
    }
}
