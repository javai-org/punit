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
     * Day-of-week partitioning covariate.
     *
     * <p>Each {@link DayGroup} declares a set of days forming a single partition.
     * Days not covered by any group form an implicit remainder partition.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @UseCase(
     *     value = "shopping.product.search",
     *     covariateDayOfWeek = {
     *         @DayGroup({SATURDAY, SUNDAY}),
     *         @DayGroup(MONDAY)
     *     }
     * )
     * public class ShoppingUseCase { }
     * }</pre>
     *
     * @return array of day groups (empty by default)
     * @see DayGroup
     */
    DayGroup[] covariateDayOfWeek() default {};

    /**
     * Time-of-day partitioning covariate.
     *
     * <p>Each string declares a time period in {@code "HH:mm/Nh"} format
     * (start time / duration in hours). Periods must not cross midnight
     * and must not overlap.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @UseCase(
     *     value = "shopping.product.search",
     *     covariateTimeOfDay = { "08:00/2h", "16:00/3h" }
     * )
     * public class ShoppingUseCase { }
     * }</pre>
     *
     * @return array of time period strings (empty by default)
     */
    String[] covariateTimeOfDay() default {};

    /**
     * Region partitioning covariate.
     *
     * <p>Each {@link RegionGroup} declares a set of ISO 3166-1 alpha-2 country
     * codes forming a single partition. Unmatched regions form an implicit
     * remainder partition.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @UseCase(
     *     value = "shopping.product.search",
     *     covariateRegion = {
     *         @RegionGroup({"FR", "DE"}),
     *         @RegionGroup({"GB", "IE"})
     *     }
     * )
     * public class ShoppingUseCase { }
     * }</pre>
     *
     * @return array of region groups (empty by default)
     * @see RegionGroup
     */
    RegionGroup[] covariateRegion() default {};

    /**
     * Timezone identity covariate.
     *
     * <p>When true, the system timezone is captured and matched as an
     * identity covariate (exact string match, no partitioning).
     *
     * @return true to enable timezone covariate (default: false)
     */
    boolean covariateTimezone() default false;

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
     * </ul>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * @UseCase(
     *     value = "shopping.product.search",
     *     covariates = {
     *         @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
     *         @Covariate(key = "prompt_version", category = CovariateCategory.CONFIGURATION)
     *     }
     * )
     * public class ShoppingUseCase { }
     * }</pre>
     *
     * @return array of custom covariates (empty by default)
     * @see Covariate
     * @see CovariateCategory
     */
    Covariate[] covariates() default {};
}
