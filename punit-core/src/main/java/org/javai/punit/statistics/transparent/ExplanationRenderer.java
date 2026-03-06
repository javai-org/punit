package org.javai.punit.statistics.transparent;

/**
 * Interface for rendering statistical explanations in various formats.
 *
 * <p>Implementations of this interface transform {@link StatisticalExplanation}
 * objects into formatted output suitable for different contexts:
 * <ul>
 *   <li>{@link TextExplanationRenderer}: Human-readable with box drawing characters</li>
 *   <li>Future: MarkdownExplanationRenderer for embedding in reports</li>
 *   <li>Future: JsonExplanationRenderer for machine-readable tooling integration</li>
 * </ul>
 */
public interface ExplanationRenderer {

    /**
     * Renders a statistical explanation to a formatted string.
     *
     * @param explanation the explanation to render
     * @return the formatted string representation
     */
    String render(StatisticalExplanation explanation);

    /**
     * Renders a statistical explanation with custom configuration.
     *
     * @param explanation the explanation to render
     * @param config the rendering configuration
     * @return the formatted string representation
     */
    default String render(StatisticalExplanation explanation, TransparentStatsConfig config) {
        return render(explanation);
    }
}

