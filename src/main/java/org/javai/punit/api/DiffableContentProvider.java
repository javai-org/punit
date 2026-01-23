package org.javai.punit.api;

import java.util.List;
import org.javai.punit.contract.UseCaseOutcome;

/**
 * Interface for use case classes that want to customize diff projection.
 *
 * <p>When a use case class implements this interface, the framework uses
 * its {@link #getDiffableContent} method instead of the default algorithm
 * based on record component reflection.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @UseCase("shopping.product.search")
 * public class ShoppingUseCase implements DiffableContentProvider {
 *
 *     @Override
 *     public List<String> getDiffableContent(UseCaseOutcome<?> outcome, int maxLineLength) {
 *         // Custom projection: summarize the typed result
 *         SearchResult result = (SearchResult) outcome.result();
 *         int productCount = result.products().size();
 *         boolean hasErrors = result.error() != null;
 *
 *         return List.of(
 *             truncate("productCount: " + productCount, maxLineLength),
 *             truncate("hasErrors: " + hasErrors, maxLineLength)
 *         );
 *     }
 *
 *     private String truncate(String s, int max) {
 *         return s.length() > max ? s.substring(0, max - 1) + "…" : s;
 *     }
 * }
 * }</pre>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li>When default toString() representations are too verbose</li>
 *   <li>When you want to exclude noisy fields (timestamps, request IDs)</li>
 *   <li>When you want to show computed summaries instead of raw data</li>
 *   <li>When domain objects need special formatting</li>
 * </ul>
 *
 * <h2>Implementation Guidelines</h2>
 * <p>The returned list should:
 * <ul>
 *   <li>Have a consistent number of lines for the same use case type</li>
 *   <li>Be alphabetically ordered by conceptual key for diff alignment</li>
 *   <li>Respect the maxLineLength to avoid diff tool wrapping</li>
 *   <li>Use ellipsis (…) to indicate truncation</li>
 * </ul>
 *
 * @see UseCaseOutcome
 */
@FunctionalInterface
public interface DiffableContentProvider {

    /**
     * Returns custom diffable content for a use case outcome.
     *
     * <p>This method is called during EXPLORE mode to generate diff-optimized
     * output for side-by-side comparison of exploration specs.
     *
     * @param outcome the use case outcome containing the typed result
     * @param maxLineLength maximum characters per line
     * @return list of formatted lines for diff comparison
     */
    List<String> getDiffableContent(UseCaseOutcome<?> outcome, int maxLineLength);
}
