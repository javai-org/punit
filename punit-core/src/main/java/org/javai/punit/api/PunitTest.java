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
 * <p>The annotated method is a normal JUnit {@code void} test. It
 * builds a typed
 * {@link org.javai.punit.api.typed.spec.ProbabilisticTest} value via
 * the compositional API and hands it to {@code Punit.run(spec)} to
 * drive the engine and translate the verdict to JUnit:
 *
 * <pre>{@code
 * @PunitTest
 * void shoppingMeetsBaseline() {
 *     Punit.run(ProbabilisticTest
 *             .testing(sampling, factors)
 *             .criterion(BernoulliPassRate.empirical())
 *             .build());
 * }
 * }</pre>
 *
 * <p>The framework runs the spec exactly once through its sampling
 * loop (the spec's {@code samples} count drives how many invocations
 * the engine performs internally — JUnit sees a single test). The
 * {@link org.javai.punit.api.typed.spec.Verdict} translates as:
 *
 * <ul>
 *   <li>{@code PASS} → JUnit pass.</li>
 *   <li>{@code FAIL} → {@link org.opentest4j.AssertionFailedError}
 *       carrying the verdict's explanation and per-criterion detail.</li>
 *   <li>{@code INCONCLUSIVE} → {@link org.opentest4j.TestAbortedException}.
 *       Configuration / environment problem (no baseline, identity
 *       mismatch, …), not a service degradation.</li>
 * </ul>
 *
 * <p>Distinct from the legacy {@code @ProbabilisticTest} annotation
 * (also in this package), which carries attributes (samples,
 * confidence, threshold) the legacy extension reads. This annotation
 * is attribute-free; every parameter lives on the typed spec the
 * method body builds and runs.
 *
 * <p>Tagged {@code "punit"} so suite-level filtering rules can pick
 * up both legacy and typed tests with one tag query.
 */
@Test
@Tag("punit")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PunitTest {
}
