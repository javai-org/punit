package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.FactorBundle;

/**
 * The result of running a {@link ProbabilisticTest} — the composed
 * verdict plus the ordered list of per-criterion results that produced
 * it.
 *
 * <p>The list carries both {@link CriterionRole#REQUIRED REQUIRED} and
 * {@link CriterionRole#REPORT_ONLY REPORT_ONLY} entries in the order the
 * spec builder registered them, so reporters can surface every criterion
 * regardless of its contribution to composition. The combined
 * {@link #verdict()} is {@link Verdict#compose(List) Verdict.compose}'d
 * from the REQUIRED subset.
 *
 * <p>{@link #intent()} carries the test's declared intent through to
 * reporting so that sentinel-grade smoke verdicts can be flagged with
 * an explicit caveat ("not sized for verification") and so that the
 * future feasibility-check machinery can decide whether a verification
 * claim is statistically supportable for the chosen sample size.
 *
 * @param verdict          the composed PASS / FAIL / INCONCLUSIVE
 * @param factors          the factor bundle observed
 * @param criterionResults the full ordered list of evaluated criteria
 * @param intent           the test's declared intent (VERIFICATION / SMOKE)
 * @param warnings         free-form warnings (e.g. "statistics wiring pending")
 */
public record ProbabilisticTestResult(
        Verdict verdict,
        FactorBundle factors,
        List<EvaluatedCriterion> criterionResults,
        TestIntent intent,
        List<String> warnings) implements EngineResult {

    public ProbabilisticTestResult {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(criterionResults, "criterionResults");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(warnings, "warnings");
        criterionResults = List.copyOf(criterionResults);
        warnings = List.copyOf(warnings);
    }
}
