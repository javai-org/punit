package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * References a static method that provides named use case configurations for EXPLORE mode.
 *
 * <p>Each configuration is a fully-constructed, immutable use case instance paired with
 * a name. The use case instance <em>is</em> the factor specification — there is no need
 * for separate factor maps or factor annotations.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ExploreExperiment(useCase = MyUseCase.class, samplesPerConfig = 20)
 * @ConfigSource("modelConfigurations")
 * void compareModels(MyUseCase useCase, OutcomeCaptor captor) {
 *     captor.record(useCase.execute(input));
 * }
 *
 * static Stream<NamedConfig<MyUseCase>> modelConfigurations() {
 *     return Stream.of(
 *         NamedConfig.of("gpt-4o-mini", new MyUseCase(llm, "gpt-4o-mini", 0.1)),
 *         NamedConfig.of("gpt-4o", new MyUseCase(llm, "gpt-4o", 0.1))
 *     );
 * }
 * }</pre>
 *
 * <p>The method must be static and return {@code Stream<NamedConfig<T>>} or
 * {@code Collection<NamedConfig<T>>}.
 *
 * @see NamedConfig
 * @see ExploreExperiment
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigSource {

    /**
     * Name of the static method providing configurations.
     *
     * <p>Resolution order:
     * <ul>
     *   <li>Simple name — search test class, then use case class</li>
     *   <li>{@code ClassName#methodName} — search test class package, then use case package</li>
     *   <li>Fully qualified — direct lookup</li>
     * </ul>
     */
    String value();
}
