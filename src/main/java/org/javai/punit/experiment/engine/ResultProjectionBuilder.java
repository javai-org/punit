package org.javai.punit.experiment.engine;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.javai.punit.api.DiffableContentProvider;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.model.ResultProjection;

/**
 * Builds diff-optimized result projections from {@link UseCaseOutcome} instances.
 *
 * <p>Supports two projection modes:
 * <ul>
 *   <li><strong>Default</strong>: Extracts content from typed results using record reflection</li>
 *   <li><strong>Custom</strong>: Uses {@link DiffableContentProvider#getDiffableContent}
 *       when a custom provider is supplied</li>
 * </ul>
 *
 * <p>Line count behavior:
 * <ul>
 *   <li>Results with fewer lines than max: padded with {@code <absent>}</li>
 *   <li>Results with exactly max lines: no placeholders</li>
 *   <li>Results with more than max lines: max content lines + {@code <truncated: +N more>}</li>
 * </ul>
 *
 * <p>Note: The truncation notice does not count toward {@code maxDiffableLines}.
 *
 * @see ResultProjection
 * @see DiffableContentProvider
 */
public class ResultProjectionBuilder {

    private final int maxDiffableLines;
    private final int maxLineLength;
    private final DiffableContentProvider customProvider;

    /**
     * Creates a builder with default projection.
     *
     * @param maxDiffableLines maximum lines to include (must be at least 1)
     * @param maxLineLength maximum characters per line (must be at least 10)
     * @throws IllegalArgumentException if parameters are out of range
     */
    public ResultProjectionBuilder(int maxDiffableLines, int maxLineLength) {
        this(maxDiffableLines, maxLineLength, null);
    }

    /**
     * Creates a builder with optional custom projection.
     *
     * @param maxDiffableLines maximum lines to include (must be at least 1)
     * @param maxLineLength maximum characters per line (must be at least 10)
     * @param customProvider custom provider, or null for default behavior
     * @throws IllegalArgumentException if parameters are out of range
     */
    public ResultProjectionBuilder(int maxDiffableLines, int maxLineLength,
                                    DiffableContentProvider customProvider) {
        if (maxDiffableLines < 1) {
            throw new IllegalArgumentException("maxDiffableLines must be at least 1");
        }
        if (maxLineLength < 10) {
            throw new IllegalArgumentException("maxLineLength must be at least 10");
        }
        this.maxDiffableLines = maxDiffableLines;
        this.maxLineLength = maxLineLength;
        this.customProvider = customProvider;
    }

    /**
     * Builds a projection from a use case outcome.
     *
     * <p>For typed results, the projection extracts diffable content by:
     * <ul>
     *   <li>If the result is a record, iterating over its components</li>
     *   <li>Otherwise, using toString() for a single-line representation</li>
     * </ul>
     *
     * @param sampleIndex the sample index (0-based)
     * @param outcome the use case outcome
     * @return the projection
     */
    public ResultProjection build(int sampleIndex, UseCaseOutcome<?> outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");

        List<String> rawLines = getDiffableLinesFromOutcome(outcome);
        List<String> normalizedLines = normalizeLineCount(rawLines);

        return new ResultProjection(
            sampleIndex,
            outcome.executionTime().toMillis(),
            normalizedLines
        );
    }

    /**
     * Builds a projection for an error case.
     *
     * @param sampleIndex the sample index (0-based)
     * @param executionTimeMs execution time in milliseconds
     * @param error the error that occurred
     * @return the projection
     */
    public ResultProjection buildError(int sampleIndex, long executionTimeMs, Throwable error) {
        Objects.requireNonNull(error, "error must not be null");
        
        List<String> lines = new ArrayList<>();
        lines.add(truncate("error: " + error.getClass().getSimpleName()));
        lines.add(truncate("message: " + firstLine(error.getMessage())));

        List<String> normalizedLines = normalizeLineCount(lines);

        return new ResultProjection(sampleIndex, executionTimeMs, normalizedLines);
    }

    /**
     * Extracts diffable content from a typed use case outcome.
     *
     * <p>The extraction strategy depends on the result type:
     * <ul>
     *   <li>If a custom provider is configured, use it</li>
     *   <li>If the result is a record, formats each component as "name: value"</li>
     *   <li>Otherwise, uses toString() formatted as a single entry</li>
     * </ul>
     */
    private List<String> getDiffableLinesFromOutcome(UseCaseOutcome<?> outcome) {
        // Use custom provider if available
        if (customProvider != null) {
            return customProvider.getDiffableContent(outcome, maxLineLength);
        }

        Object result = outcome.result();
        if (result == null) {
            return List.of("result: null");
        }

        Class<?> resultClass = result.getClass();
        if (resultClass.isRecord()) {
            return extractRecordComponents(result, resultClass);
        }

        // Fallback: single-line toString representation
        String valueStr = normalizeValue(result.toString());
        return List.of(truncate("result: " + valueStr));
    }

    private List<String> extractRecordComponents(Object record, Class<?> recordClass) {
        RecordComponent[] components = recordClass.getRecordComponents();
        return Arrays.stream(components)
            .sorted(Comparator.comparing(RecordComponent::getName))
            .map(comp -> formatRecordComponent(record, comp))
            .toList();
    }

    private String formatRecordComponent(Object record, RecordComponent component) {
        String name = component.getName();
        try {
            Object value = component.getAccessor().invoke(record);
            String valueStr = normalizeValue(value == null ? "null" : value.toString());
            return truncate(name + ": " + valueStr);
        } catch (Exception e) {
            return truncate(name + ": <error>");
        }
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private List<String> normalizeLineCount(List<String> lines) {
        List<String> normalized = new ArrayList<>();

        // Add up to maxDiffableLines of actual content
        int contentLinesToAdd = Math.min(lines.size(), maxDiffableLines);
        for (int i = 0; i < contentLinesToAdd; i++) {
            normalized.add(lines.get(i));
        }

        // Add truncation notice if there are more lines (does NOT count toward maxDiffableLines)
        int remaining = lines.size() - contentLinesToAdd;
        if (remaining > 0) {
            normalized.add(truncationNotice(remaining));
        }

        // Pad with <absent> if needed to reach maxDiffableLines
        // (only when no truncation occurred)
        while (normalized.size() < maxDiffableLines) {
            normalized.add(ResultProjection.ABSENT);
        }

        return List.copyOf(normalized);
    }

    private String truncate(String text) {
        if (text.length() <= maxLineLength) {
            return text;
        }
        return text.substring(0, maxLineLength - 1) + "…";
    }

    private String truncationNotice(int remaining) {
        return "<truncated: +" + remaining + " more>";
    }

    private String firstLine(String text) {
        if (text == null) {
            return "null";
        }
        int newline = text.indexOf('\n');
        return newline > 0 ? text.substring(0, newline) : text;
    }
}

