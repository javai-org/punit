package org.javai.punit.internal.engine.criteria;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.spec.BaselineProvider;
import org.javai.punit.api.spec.Criterion;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.internal.reporting.InfeasibilityMessageRenderer;
import org.javai.punit.statistics.StatisticalDefaults;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator.FeasibilityResult;

/**
 * Pre-flight feasibility gate for probabilistic tests.
 *
 * <p>Per {@link TestIntent}'s contract:
 *
 * <ul>
 *   <li>{@link TestIntent#VERIFICATION} (default) — if the configured
 *       sample size cannot support a confidence-backed claim against
 *       the resolved baseline rate, the test fails fast as misconfigured
 *       (an {@link IllegalStateException}) <em>before</em> the engine
 *       runs any samples.</li>
 *   <li>{@link TestIntent#SMOKE} — silent. The developer has
 *       explicitly declared SMOKE intent, which means "I know this is
 *       undersized; treat it as a sentinel." The gate produces no
 *       warning and the run proceeds.</li>
 * </ul>
 *
 * <p>The one exception is the <strong>soundness floor</strong>: a
 * configured confidence level below
 * {@link StatisticalDefaults#SOUNDNESS_FLOOR_CONFIDENCE} aborts
 * regardless of intent. A test that cannot make a claim at the floor's
 * confidence level cannot underwrite a verdict, and Smoke-intent does
 * not buy past that.
 *
 * <p>The check applies to {@link PassRate} criteria — both contractual
 * (declared SLA / SLO / POLICY threshold) and empirical (threshold
 * derived from a resolved baseline). A contractual claim is no less
 * in need of statistical underwriting than an empirical one: n=50
 * with a 99.99% target at 95% confidence is infeasible regardless of
 * whether the 99.99% came from a measurement or a contract.
 * Non-{@code PassRate} criteria (e.g. {@code PercentileLatency}) are
 * skipped pending their own feasibility model.
 *
 * <p>Empirical criteria require a resolvable baseline at check time.
 * When no baseline file matches, feasibility is silently deferred —
 * the criterion will produce {@code INCONCLUSIVE} at evaluate time,
 * which is the correct outcome for "no baseline available."
 *
 * <p>Statistics-isolation rule: the math lives in
 * {@link VerificationFeasibilityEvaluator}; this gate only
 * orchestrates. Diagnostic prose comes from
 * {@link InfeasibilityMessageRenderer}.
 */
public final class Feasibility {

    private Feasibility() { }

    /**
     * Check feasibility of a single {@code PassRate} criterion
     * against a resolved baseline.
     *
     * @return an empty list. Throws {@link IllegalStateException}
     *         when intent is {@link TestIntent#VERIFICATION} and the
     *         criterion is infeasible. SMOKE-intent infeasibility is
     *         silent — see the class javadoc.
     */
    public static List<String> check(
            int samples,
            Criterion<?, ?> criterion,
            String useCaseId,
            FactorBundle factors,
            TestIntent intent,
            BaselineProvider provider) {
        if (!(criterion instanceof PassRate<?> bernoulli)) {
            return List.of();
        }
        // Soundness floor — applies regardless of intent. A test
        // configured below the framework's confidence floor cannot
        // underwrite a verdict; even a Smoke-intent test does not
        // silently produce results at that confidence. This check
        // runs before the rate resolution below, so an empirical
        // criterion configured below the floor aborts here regardless
        // of whether its baseline is on disk yet.
        if (bernoulli.confidence() < StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE) {
            throw new IllegalStateException(
                    InfeasibilityMessageRenderer.renderSoundnessFloorBreach(
                            useCaseId,
                            bernoulli.confidence(),
                            StatisticalDefaults.SOUNDNESS_FLOOR_CONFIDENCE));
        }
        double rate;
        if (bernoulli.isEmpirical()) {
            Optional<PassRateStatistics> baseline = provider.baselineFor(
                    useCaseId, factors, criterion.name(), PassRateStatistics.class);
            if (baseline.isEmpty()) {
                return List.of();
            }
            rate = baseline.get().observedPassRate();
        } else {
            OptionalDouble target = bernoulli.contractualTarget();
            if (target.isEmpty()) {
                return List.of();
            }
            rate = target.getAsDouble();
        }
        if (rate <= 0.0 || rate >= 1.0) {
            // The evaluator requires the target in (0, 1) exclusive. A
            // rate of exactly 0 or 1 is degenerate; the criterion will
            // surface that downstream as a deterministic pass/fail
            // (contractual) or an INCONCLUSIVE verdict (empirical).
            return List.of();
        }
        FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(
                samples, rate, bernoulli.confidence());
        if (result.feasible()) {
            return List.of();
        }
        if (intent == TestIntent.VERIFICATION) {
            throw new IllegalStateException(
                    InfeasibilityMessageRenderer.render(useCaseId, result, false));
        }
        // SMOKE intent: the developer has declared "I know this is
        // undersized; treat it as a sentinel." Silent — no warning.
        return List.of();
    }
}
