package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a setter method on a use case for automatic factor injection.
 *
 * <p>When a use case is registered with {@code UseCaseProvider.registerAutoWired()},
 * the provider automatically invokes annotated setters with factor values.
 *
 * <h2>Factor Name Resolution</h2>
 * <p>The factor name is determined by:
 * <ol>
 *   <li>The annotation's {@code value} parameter, if provided</li>
 *   <li>Otherwise, derived from the method name by removing the "set" prefix
 *       and lowercasing the first character (e.g., {@code setTemperature} → "temperature")</li>
 * </ol>
 *
 * @deprecated Mutable factor setters risk violating the i.i.d. assumption that underpins
 * PUnit's statistical model. Use {@link UseCaseProvider#registerWithFactors} to configure
 * factors at construction time instead. See the user guide section on immutable use cases.
 * @see FactorGetter
 */
@Deprecated(since = "0.6.0", forRemoval = true)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FactorSetter {

    /**
     * The name of the factor to inject.
     *
     * <p>If empty (the default), the factor name is derived from the method name
     * by removing the "set" prefix and lowercasing the first character.
     *
     * @return the factor name, or empty to derive from method name
     */
    String value() default "";
}
