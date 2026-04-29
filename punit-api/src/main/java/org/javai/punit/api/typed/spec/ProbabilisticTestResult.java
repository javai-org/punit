package org.javai.punit.api.typed.spec;

import java.util.List;
import java.util.Objects;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.covariate.CovariateAlignment;

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
 * <p>{@link #covariates()} carries the alignment between the run's
 * resolved covariate profile and the matched baseline's covariate
 * profile (when an empirical criterion matched one). The structured
 * value flows through to verdict-text renderers, HTML report
 * emitters, and JSON sinks, mirroring the legacy pipeline's
 * {@code CovariateStatus} so downstream tooling sees the same shape
 * from both pipelines.
 *
 * @param verdict          the composed PASS / FAIL / INCONCLUSIVE
 * @param factors          the factor bundle observed
 * @param criterionResults the full ordered list of evaluated criteria
 * @param intent           the test's declared intent (VERIFICATION / SMOKE)
 * @param warnings         free-form warnings (e.g. "statistics wiring pending")
 * @param covariates       observed-vs-baseline covariate alignment
 */
public record ProbabilisticTestResult(
        Verdict verdict,
        FactorBundle factors,
        List<EvaluatedCriterion> criterionResults,
        TestIntent intent,
        List<String> warnings,
        CovariateAlignment covariates) implements EngineResult {

    public ProbabilisticTestResult {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(factors, "factors");
        Objects.requireNonNull(criterionResults, "criterionResults");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(covariates, "covariates");
        criterionResults = List.copyOf(criterionResults);
        warnings = List.copyOf(warnings);
    }

    /**
     * Convenience constructor for callers that don't carry covariate
     * alignment. Equivalent to the canonical constructor with
     * {@link CovariateAlignment#none()}.
     */
    public ProbabilisticTestResult(
            Verdict verdict,
            FactorBundle factors,
            List<EvaluatedCriterion> criterionResults,
            TestIntent intent,
            List<String> warnings) {
        this(verdict, factors, criterionResults, intent, warnings,
                CovariateAlignment.none());
    }

    /**
     * @return a copy of this result with the given covariate
     *         alignment substituted. Used by the JUnit pipeline to
     *         post-stamp the observed profile (resolved at the
     *         test-extension boundary) onto the result coming back
     *         from {@code Spec.conclude}.
     */
    public ProbabilisticTestResult withCovariates(CovariateAlignment covariates) {
        return new ProbabilisticTestResult(
                verdict, factors, criterionResults, intent, warnings, covariates);
    }
}
