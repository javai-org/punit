package org.javai.punit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the source of a covariate value.
 *
 * <p>Use this annotation on instance methods in a use case class to provide
 * covariate values at runtime. This is the preferred way to supply values
 * for CONFIGURATION covariates that depend on injected dependencies.
 *
 * <h2>Covariate Key Resolution</h2>
 * <p>The covariate key is determined by:
 * <ol>
 *   <li>The annotation's {@code value} parameter, if provided</li>
 *   <li>Otherwise, derived from the method name by removing the "get" prefix
 *       and lowercasing the first character (e.g., {@code getTemperature} → "temperature")</li>
 * </ol>
 *
 * <h2>Value Resolution Hierarchy</h2>
 * <p>Covariate values are resolved in this order:
 * <ol>
 *   <li>{@code @CovariateSource} method (if present and returns non-null)</li>
 *   <li>System property: {@code org.javai.punit.covariate.<key>}</li>
 *   <li>Environment variable: {@code ORG_JAVAI_PUNIT_COVARIATE_<KEY>}</li>
 *   <li>Default resolver (for standard covariates like TIME_OF_DAY)</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @UseCase(
 *     value = "ProductSearch",
 *     categorizedCovariates = {
 *         @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
 *         @Covariate(key = "temperature", category = CovariateCategory.CONFIGURATION)
 *     }
 * )
 * public class ProductSearchUseCase {
 *
 *     @CovariateSource("llm_model")  // explicit key
 *     public String getModel() {
 *         return llmClient.getModelName();
 *     }
 *
 *     @CovariateSource  // key derived as "temperature"
 *     public double getTemperature() {
 *         return temperature;
 *     }
 * }
 * }</pre>
 *
 * <h2>Return Types</h2>
 * <p>The annotated method may return:
 * <ul>
 *   <li>{@code String} — wrapped as {@code CovariateValue.StringValue}</li>
 *   <li>Any other type — converted via {@code toString()}</li>
 *   <li>{@code CovariateValue} — used directly (for complex types)</li>
 * </ul>
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>Method must be public</li>
 *   <li>Method must be an instance method (not static)</li>
 *   <li>Method must take no parameters</li>
 *   <li>Method must not throw checked exceptions</li>
 * </ul>
 *
 * @see Covariate
 * @see CovariateCategory
 * @see UseCase#categorizedCovariates()
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CovariateSource {

    /**
     * The covariate key this method provides a value for.
     *
     * <p>If empty (the default), the key is derived from the method name
     * by removing the "get" prefix and lowercasing the first character.
     *
     * @return the covariate key, or empty to derive from method name
     */
    String value() default "";
}

