package org.javai.punit.engine.criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.spec.BaselineProvider;
import org.javai.punit.api.spec.Criterion;
import org.javai.punit.api.spec.PassRateStatistics;
import org.javai.punit.reporting.InfeasibilityMessageRenderer;
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
 *   <li>{@link TestIntent#SMOKE} — undersized configurations are
 *       allowed; a warning is returned for the caller to surface.</li>
 * </ul>
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
     * @return a list of warnings (one per infeasible-but-tolerated
     *         SMOKE criterion). Throws {@link IllegalStateException}
     *         instead when intent is {@link TestIntent#VERIFICATION}
     *         and the criterion is infeasible.
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
        String diagnostic = InfeasibilityMessageRenderer.render(useCaseId, result, false);
        if (intent == TestIntent.VERIFICATION) {
            throw new IllegalStateException(diagnostic);
        }
        List<String> warnings = new ArrayList<>(1);
        warnings.add("[" + intent + "]" + diagnostic);
        return warnings;
    }
}
