package org.javai.punit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a custom covariate with its category.
 *
 * <p>Use this annotation within {@link UseCase#covariates()} to define
 * custom contextual factors that may influence use case behavior, along
 * with their matching semantics.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @UseCase(
 *     value = "ProductSearch",
 *     covariates = {
 *         @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION),
 *         @Covariate(key = "prompt_version", category = CovariateCategory.CONFIGURATION),
 *         @Covariate(key = "cache_warm", category = CovariateCategory.DATA_STATE)
 *     }
 * )
 * public class ProductSearchUseCase { }
 * }</pre>
 *
 * <h2>Category Effects</h2>
 * <ul>
 *   <li><strong>CONFIGURATION:</strong> Hard gate — baseline selection fails if no match</li>
 *   <li><strong>TEMPORAL, INFRASTRUCTURE, EXTERNAL_DEPENDENCY, DATA_STATE, OPERATIONAL:</strong>
 *       Soft match — test proceeds with category-specific warning</li>
 * </ul>
 *
 * @see CovariateCategory
 * @see UseCase#covariates()
 * @see CovariateSource
 */
@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Covariate {

    /**
     * The covariate key used in baseline specs and profile storage.
     *
     * <p>Best practices:
     * <ul>
     *   <li>Use lowercase with underscores: "llm_model", "api_version"</li>
     *   <li>Be descriptive but concise</li>
     *   <li>Avoid special characters</li>
     * </ul>
     *
     * @return the covariate key
     */
    String key();

    /**
     * The category determining matching behavior.
     *
     * <p>Choose carefully:
     * <ul>
     *   <li>Use CONFIGURATION for deliberate choices that explain behavior differences</li>
     *   <li>Use TEMPORAL/INFRASTRUCTURE for environmental factors</li>
     *   <li>Use DATA_STATE for data context factors</li>
     * </ul>
     *
     * @return the covariate category
     */
    CovariateCategory category();
}
