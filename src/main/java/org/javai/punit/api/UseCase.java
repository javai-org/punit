package org.javai.punit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or method as a PUnit use case and optionally specifies its ID.
 *
 * <p>Use cases are the core abstraction in PUnit for encapsulating behavior
 * that needs probabilistic testing or experimentation. They provide:
 * <ul>
 *   <li>A stable identifier for linking experiments, specs, and tests</li>
 *   <li>Encapsulation of success/failure observation logic</li>
 *   <li>A bridge between test assertions and production code</li>
 * </ul>
 *
 * <h2>Class-Level Usage (Recommended)</h2>
 *
 * <h3>Default ID (simple class name)</h3>
 * <pre>{@code
 * // ID is "ShoppingUseCase"
 * @UseCase
 * public class ShoppingUseCase {
 *     public UseCaseOutcome<SearchResult> searchProducts(String query, UseCaseContext ctx) {
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
 * <h2>Method-Level Usage</h2>
 * <pre>{@code
 * @UseCase("usecase.email.validation")
 * UseCaseOutcome<ValidationResult> validateEmail(String email, UseCaseContext context) {
 *     return UseCaseOutcome
 *         .withContract(EMAIL_CONTRACT)
 *         .input(email)
 *         .execute(e -> emailValidator.validate(e))
 *         .build();
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
@Target({ElementType.TYPE, ElementType.METHOD})
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
     * Human-readable description of what this use case tests.
     *
     * <p>This is used for documentation and logging purposes.
     *
     * @return description of the use case
     */
    String description() default "";

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

    /**
     * Standard covariates that may influence this use case's performance.
     *
     * <p>Declared covariates:
     * <ul>
     *   <li>Are captured during MEASURE experiments</li>
     *   <li>Contribute to the invocation footprint (by name)</li>
     *   <li>Participate in baseline selection and conformance checking</li>
     * </ul>
     *
     * <p>Order matters: earlier covariates are prioritized during matching.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @UseCase(
     *     value = "shopping.product.search",
     *     covariates = {
     *         StandardCovariate.WEEKDAY_VERSUS_WEEKEND,
     *         StandardCovariate.TIME_OF_DAY
     *     }
     * )
     * public class ShoppingUseCase { }
     * }</pre>
     *
     * @return array of standard covariates (empty by default)
     * @see StandardCovariate
     */
    StandardCovariate[] covariates() default {};

    /**
     * Custom covariate keys for user-defined contextual factors (legacy).
     *
     * <p><strong>Deprecated:</strong> Use {@link #categorizedCovariates()} instead
     * to specify both key and category.
     *
     * <p>Covariates declared here are treated as having {@link CovariateCategory#INFRASTRUCTURE}
     * category (soft match with warning on mismatch).
     *
     * <p>Custom covariates are resolved from (in order):
     * <ol>
     *   <li>System property: {@code -D{key}=value}</li>
     *   <li>Environment variable: {@code KEY=value} (uppercased)</li>
     *   <li>PUnit environment map (programmatic)</li>
     * </ol>
     *
     * <p>If a custom covariate is not found in the environment, its value
     * is recorded as "undefined". Values of "undefined" never match, even if
     * both baseline and test have "undefined".
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @UseCase(
     *     value = "shopping.product.search",
     *     customCovariates = { "hosting_environment", "feature_flag_new_ranking" }
     * )
     * public class ShoppingUseCase { }
     * }</pre>
     *
     * @return array of custom covariate keys (empty by default)
     * @see #categorizedCovariates()
     */
    String[] customCovariates() default {};

    /**
     * Custom covariates with explicit categories.
     *
     * <p>Use this to declare custom covariates with their matching semantics:
     * <ul>
     *   <li>{@link CovariateCategory#CONFIGURATION}: Hard gate — fails if no match</li>
     *   <li>{@link CovariateCategory#TEMPORAL}: Soft match with temporal-specific warning</li>
     *   <li>{@link CovariateCategory#INFRASTRUCTURE}: Soft match with infrastructure warning</li>
     *   <li>{@link CovariateCategory#OPERATIONAL}: Soft match with operational warning</li>
     *   <li>{@link CovariateCategory#EXTERNAL_DEPENDENCY}: Soft match for external services</li>
     *   <li>{@link CovariateCategory#DATA_STATE}: Soft match for data context</li>
     *   <li>{@link CovariateCategory#INFORMATIONAL}: Ignored in matching, for traceability only</li>
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @UseCase(
     *     value = "shopping.product.search",
     *     covariates = { StandardCovariate.TIME_OF_DAY },
     *     categorizedCovariates = {
     *         @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
     *         @Covariate(key = "prompt_version", category = CovariateCategory.CONFIGURATION),
     *         @Covariate(key = "run_id", category = CovariateCategory.INFORMATIONAL)
     *     }
     * )
     * public class ShoppingUseCase { }
     * }</pre>
     *
     * @return array of categorized covariates (empty by default)
     * @see Covariate
     * @see CovariateCategory
     */
    Covariate[] categorizedCovariates() default {};
}

