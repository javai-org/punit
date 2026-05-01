package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Marks a method as an experiment in the typed-compositional
 * authoring model — a measure, explore, or optimize whose role is
 * to produce an artefact (baseline file, exploration grid,
 * optimization history) rather than a verdict.
 *
 * <p>The annotated method is a normal JUnit {@code void} test. Its
 * body builds a typed experiment spec via the compositional API and
 * runs it:
 *
 * <pre>{@code
 * @Experiment
 * void shoppingBaseline() {
 *     PUnit.measuring(shoppingSampling(1000), shoppingFactors()).run();
 * }
 *
 * @Experiment
 * void shoppingAcrossModels() {
 *     PUnit.exploring(shoppingSampling(100))
 *             .grid(new Factors("gpt-4o", 0.0),
 *                   new Factors("gpt-4o", 0.5),
 *                   new Factors("claude-3-sonnet", 0.0))
 *             .run();
 * }
 *
 * @Experiment
 * void shoppingOptimize() {
 *     PUnit.optimizing(shoppingSampling(50))
 *             .initialFactors(new Factors("gpt-4o", 0.0))
 *             .stepper(stepper)
 *             .maximize(scorer)
 *             .maxIterations(10)
 *             .run();
 * }
 * }</pre>
 *
 * <p>One annotation, three kinds — the kind is chosen at the
 * method body's verb ({@code measuring} / {@code exploring} /
 * {@code optimizing}).
 *
 * <p>Tagged {@code "punit-experiment"} so the standard test run
 * (filtered by Gradle / Maven configuration) excludes them by
 * default. Experiments run only when explicitly invoked, typically
 * via the {@code experiment} / {@code exp} Gradle tasks the punit
 * plugin registers.
 */
@Test
@Tag("punit-experiment")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Experiment {
}
