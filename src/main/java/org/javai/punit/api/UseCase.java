package org.javai.punit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a PUnit use case and optionally specifies its ID.
 *
 * <p>Use cases are the core abstraction in PUnit for encapsulating LLM-powered
 * behavior that needs probabilistic testing. They provide:
 * <ul>
 *   <li>A stable identifier for linking experiments, specs, and tests</li>
 *   <li>Encapsulation of success/failure observation logic</li>
 *   <li>A bridge between test assertions and production code</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <h3>Default ID (simple class name)</h3>
 * <pre>{@code
 * // ID is "ShoppingUseCase"
 * public class ShoppingUseCase {
 *     public UseCaseResult searchProducts(String query, UseCaseContext ctx) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <h3>Custom ID</h3>
 * <pre>{@code
 * @UseCase("shopping.product.search")
 * public class ShoppingUseCase {
 *     // ID is "shopping.product.search"
 * }
 * }</pre>
 *
 * <h2>ID Resolution</h2>
 * <p>When a use case class is referenced in {@code @Experiment} or {@code @ProbabilisticTest},
 * the ID is resolved as follows:
 * <ol>
 *   <li>If {@code @UseCase} annotation is present with a non-empty value, use that value</li>
 *   <li>Otherwise, use the simple class name (e.g., "ShoppingUseCase")</li>
 * </ol>
 *
 * <p>The resolved ID is used for:
 * <ul>
 *   <li>Baseline file naming: {@code baselines/{id}.yaml}</li>
 *   <li>Spec file location: {@code punit/specs/{id}.yaml}</li>
 *   <li>Logging and diagnostics</li>
 * </ul>
 *
 * @see UseCaseProvider
 * @see ProbabilisticTest#useCase()
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {

    /**
     * The use case identifier.
     *
     * <p>If empty (default), the simple class name is used as the ID.
     *
     * <p>Best practices for custom IDs:
     * <ul>
     *   <li>Use lowercase with dots as separators: "shopping.product.search"</li>
     *   <li>Be descriptive but concise</li>
     *   <li>Avoid special characters that are invalid in file paths</li>
     * </ul>
     *
     * @return the use case ID, or empty string to use class name
     */
    String value() default "";

    /**
     * Maximum number of content lines to include in EXPLORE result projections.
     *
     * <p>When a result has fewer lines than this limit, remaining lines
     * are filled with {@code <absent>}. When a result has more lines,
     * exactly {@code maxDiffableLines} content lines are shown, followed
     * by a {@code <truncated: +N more>} notice (which does not count
     * toward this limit).
     *
     * <p>For perfect diff alignment across configs, set this high enough
     * to avoid truncation in typical cases.
     *
     * @return maximum content lines (default: 5)
     */
    int maxDiffableLines() default 5;

    /**
     * Maximum characters per line in diffable content.
     *
     * <p>Lines exceeding this length are truncated with an ellipsis (…).
     * This ensures content displays well in side-by-side diff tools.
     *
     * <p>Consider your diff tool's typical viewport when setting this:
     * <ul>
     *   <li>40: Conservative, works with narrow panes</li>
     *   <li>60: Balanced for most IDEs in split view</li>
     *   <li>80: Full terminal width</li>
     * </ul>
     *
     * @return maximum line length (default: 60)
     */
    int diffableContentMaxLineLength() default 60;
}

