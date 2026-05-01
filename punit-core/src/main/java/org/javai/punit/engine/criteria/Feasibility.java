package org.javai.punit.engine.criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * <p>The check applies only to <em>empirical</em> {@link BernoulliPassRate}
 * criteria — the only place the engine makes a confidence-backed
 * statistical claim today. Contractual {@code .meeting(threshold, origin)}
 * paths use a deterministic {@code observed >= threshold} comparison and
 * have no feasibility concern. Non-{@code BernoulliPassRate} criteria
 * (e.g. {@code PercentileLatency}) are skipped pending their own
 * feasibility model.
 *
 * <p>Only criteria whose baseline is resolvable at check time can be
 * checked. When no baseline file matches, feasibility is silently
 * deferred — the criterion will produce {@code INCONCLUSIVE} at evaluate
 * time, which is the correct outcome for "no baseline available."
 *
 * <p>Statistics-isolation rule: the math lives in
 * {@link VerificationFeasibilityEvaluator}; this gate only
 * orchestrates. Diagnostic prose comes from
 * {@link InfeasibilityMessageRenderer}.
 */
public final class Feasibility {

    private Feasibility() { }

    /**
     * Check feasibility of a single {@code BernoulliPassRate} criterion
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
        if (!(criterion instanceof BernoulliPassRate<?> bernoulli) || !criterion.isEmpirical()) {
            return List.of();
        }
        Optional<PassRateStatistics> baseline = provider.baselineFor(
                useCaseId, factors, criterion.name(), PassRateStatistics.class);
        if (baseline.isEmpty()) {
            return List.of();
        }
        double rate = baseline.get().observedPassRate();
        if (rate <= 0.0 || rate >= 1.0) {
            // The evaluator requires the target in (0, 1) exclusive. A
            // rate of exactly 0 or 1 is a degenerate baseline; the engine
            // will surface that downstream as an INCONCLUSIVE verdict.
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
