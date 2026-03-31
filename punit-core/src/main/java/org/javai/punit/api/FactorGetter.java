package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a getter method on a use case as providing a factor's current value.
 *
 * <p>This annotation is the counterpart to {@link FactorSetter}. Together they
 * define the read/write interface for a factor on a use case.
 *
 * <h2>Factor Name Resolution</h2>
 * <p>The factor name is determined by:
 * <ol>
 *   <li>The annotation's {@code value} parameter, if provided</li>
 *   <li>Otherwise, derived from the method name by removing the "get" prefix
 *       and lowercasing the first character (e.g., {@code getTemperature} → "temperature")</li>
 * </ol>
 *
 * @deprecated With immutable use cases, factor values are set at construction time and read
 * via standard accessors. This annotation is no longer needed. Use
 * {@link UseCaseProvider#registerWithFactors} to configure factors at construction time.
 * See the user guide section on immutable use cases.
 * @see FactorSetter
 * @see ControlFactor
 */
@Deprecated(since = "0.5.3", forRemoval = true)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FactorGetter {

    /**
     * The name of the factor this method provides values for.
     *
     * <p>If empty (the default), the factor name is derived from the method name
     * by removing the "get" prefix and lowercasing the first character.
     *
     * @return the factor name, or empty to derive from method name
     */
    String value() default "";
}
