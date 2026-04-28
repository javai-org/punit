package org.javai.punit.junit5;

import java.util.List;
import java.util.Objects;

import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.api.typed.spec.CriterionResult;
import org.javai.punit.api.typed.spec.EngineResult;
import org.javai.punit.api.typed.spec.EvaluatedCriterion;
import org.javai.punit.api.typed.spec.ProbabilisticTest;
import org.javai.punit.api.typed.spec.ProbabilisticTestResult;
import org.javai.punit.api.typed.spec.Verdict;
import org.javai.punit.engine.Engine;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * The bridge from a typed spec value to a JUnit assertion outcome.
 *
 * <p>Authors call {@link #run(ProbabilisticTest)} from inside a
 * {@link org.javai.punit.api.PunitTest @PunitTest} method to drive
 * the spec through the typed engine and translate the resulting
 * verdict into JUnit's pass / fail / aborted vocabulary:
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
 * <p>Translation:
 *
 * <ul>
 *   <li>{@link Verdict#PASS} → returns normally.</li>
 *   <li>{@link Verdict#FAIL} → throws
 *       {@link AssertionFailedError} with the verdict's
 *       explanation and the per-criterion detail.</li>
 *   <li>{@link Verdict#INCONCLUSIVE} → throws
 *       {@link TestAbortedException} with the verdict's
 *       explanation. JUnit treats this as a skipped (aborted)
 *       test — the right semantic for "couldn't reach a verdict"
 *       (no baseline, identity mismatch, etc.) which is
 *       configuration drift, not service degradation.</li>
 * </ul>
 *
 * <p>The {@link BaselineProvider} the engine consumes is resolved
 * by {@link BaselineProviderResolver}. Stage-5 Slice B uses
 * {@link BaselineProvider#EMPTY} unconditionally; Slice D wires
 * the real precedence (system property → JUnit configuration
 * parameter → project convention directory).
 */
public final class Punit {

    private Punit() { }

    /**
     * Drives {@code spec} through the engine and translates the
     * verdict to a JUnit outcome.
     *
     * @throws NullPointerException     if {@code spec} is null
     * @throws AssertionFailedError     when the spec produces
     *                                  {@link Verdict#FAIL}
     * @throws TestAbortedException     when the spec produces
     *                                  {@link Verdict#INCONCLUSIVE}
     */
    public static void run(ProbabilisticTest spec) {
        Objects.requireNonNull(spec, "spec");
        EngineResult result = new Engine(BaselineProviderResolver.resolve()).run(spec);
        if (!(result instanceof ProbabilisticTestResult typed)) {
            throw new IllegalStateException(
                    "Engine produced unexpected result type for ProbabilisticTest: "
                            + result.getClass().getName());
        }
        translate(typed);
    }

    private static void translate(ProbabilisticTestResult result) {
        Verdict verdict = result.verdict();
        if (verdict == Verdict.PASS) {
            return;
        }
        String message = formatMessage(result);
        if (verdict == Verdict.INCONCLUSIVE) {
            throw new TestAbortedException(message);
        }
        throw new AssertionFailedError(message);
    }

    private static String formatMessage(ProbabilisticTestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.verdict());
        List<EvaluatedCriterion> evaluated = result.criterionResults();
        if (!evaluated.isEmpty()) {
            sb.append('\n');
            for (EvaluatedCriterion entry : evaluated) {
                CriterionResult cr = entry.result();
                sb.append("  [").append(entry.role()).append("] ")
                        .append(cr.criterionName()).append(" → ")
                        .append(cr.verdict()).append(": ")
                        .append(cr.explanation()).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
