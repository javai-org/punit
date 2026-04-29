package org.javai.punit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Marks a method as a probabilistic test in the typed-compositional
 * authoring model.
 *
 * <p>The annotated method is a normal JUnit {@code void} test. Its
 * body builds a typed test spec via the compositional API and asserts
 * that it reaches a {@code PASS} verdict:
 *
 * <pre>{@code
 * @ProbabilisticTest
 * void shoppingMeetsBaseline() {
 *     PUnit.testing(this::shoppingBaseline)
 *             .samples(100)
 *             .criterion(BernoulliPassRate.empirical())
 *             .assertPasses();
 * }
 * }</pre>
 *
 * <p>The framework runs the spec exactly once through its sampling
 * loop (the spec's {@code samples} count drives how many invocations
 * the engine performs internally — JUnit sees a single test). The
 * resulting {@link org.javai.punit.api.typed.spec.Verdict} translates:
 *
 * <ul>
 *   <li>{@code PASS} → JUnit pass.</li>
 *   <li>{@code FAIL} → {@link org.opentest4j.AssertionFailedError}
 *       carrying the verdict's explanation and per-criterion detail.</li>
 *   <li>{@code INCONCLUSIVE} → {@link org.opentest4j.TestAbortedException}
 *       — configuration / environment problem (no baseline, identity
 *       mismatch, …), not a service degradation.</li>
 * </ul>
 *
 * <p>Distinct from the legacy
 * {@link org.javai.punit.api.legacy.ProbabilisticTest @ProbabilisticTest}
 * which carries attributes (samples, confidence, threshold) the legacy
 * extension reads. This annotation is attribute-free; every parameter
 * lives on the typed spec the method body builds and runs.
 *
 * <p>Tagged {@code "punit"} so suite-level filtering rules can pick
 * up both legacy and typed tests with one tag query.
 */
@Test
@Tag("punit")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProbabilisticTest {
}
