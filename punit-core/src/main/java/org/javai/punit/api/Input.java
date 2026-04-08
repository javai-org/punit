package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves ambiguity when the framework cannot determine which parameter
 * should receive {@link InputSource} injection.
 *
 * <p>In most experiment methods this annotation is unnecessary. The framework
 * auto-detects the input parameter by excluding known framework types
 * ({@link OutcomeCaptor}, UseCase, {@link TokenChargeRecorder}) and annotated
 * parameters ({@link Factor @Factor}, {@link ControlFactor @ControlFactor}).
 * When exactly one candidate remains, it receives the input automatically.
 *
 * <p>Use {@code @Input} only when the method signature leaves more than one
 * candidate and the framework would otherwise pick the wrong one.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ExploreExperiment(useCase = MyUseCase.class, samplesPerConfig = 10)
 * @InputSource(file = "golden/instructions.json")
 * void explore(
 *         MyUseCase useCase,
 *         @Input GoldenInput input,   // disambiguates — Context would be chosen otherwise
 *         Context context,
 *         OutcomeCaptor captor
 * ) { ... }
 * }</pre>
 *
 * @see InputSource
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Input {
}
