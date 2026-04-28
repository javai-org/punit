package org.javai.punit.junit5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.api.typed.spec.Criterion;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator;
import org.javai.punit.statistics.VerificationFeasibilityEvaluator.FeasibilityResult;

/**
 * Pre-flight feasibility gate for probabilistic tests under JUnit.
 *
 * <p>Per {@link TestIntent}'s contract:
 *
 * <ul>
 *   <li>{@link TestIntent#VERIFICATION} (default) — if the configured
 *       sample size cannot support a confidence-backed claim against
 *       the resolved baseline rate, the test fails fast as misconfigured
 *       (an {@link IllegalStateException}) <em>before</em> the engine
 *       runs any samples. The minimum required N is reported in the
 *       diagnostic.</li>
 *   <li>{@link TestIntent#SMOKE} — undersized configurations are
 *       allowed; the framework records a {@link Warning} and surfaces
 *       it on the result so reports can label the verdict appropriately.</li>
 * </ul>
 *
 * <p>The check applies only to <em>empirical</em> {@link BernoulliPassRate}
 * criteria — the only place the typed pipeline makes a confidence-backed
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
 */
final class Feasibility {

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
    static List<String> check(
            int samples,
            Criterion<?, ?> criterion,
            String useCaseId,
            FactorBundle factors,
            TestIntent intent,
            BaselineProvider provider) {
        if (!(criterion instanceof BernoulliPassRate<?> bernoulli) || !criterion.isEmpirical()) {
            // Non-Bernoulli or contractual: feasibility doesn't apply here.
            return List.of();
        }
        Optional<PassRateStatistics> baseline = provider.baselineFor(
                useCaseId, factors, criterion.name(), PassRateStatistics.class);
        if (baseline.isEmpty()) {
            // No baseline resolvable. The criterion will produce INCONCLUSIVE
            // at evaluate time — correct outcome; nothing to check here.
            return List.of();
        }
        double rate = baseline.get().observedPassRate();
        if (rate <= 0.0 || rate >= 1.0) {
            // Edge case: the evaluator requires the target in (0, 1) exclusive.
            // Rates of exactly 0 or 1 are degenerate baselines; let the engine
            // handle them downstream rather than reject upstream.
            return List.of();
        }
        FeasibilityResult result = VerificationFeasibilityEvaluator.evaluate(
                samples, rate, bernoulli.confidence());
        if (result.feasible()) {
            return List.of();
        }
        String diagnostic = formatInfeasibilityDiagnostic(
                criterion.name(), result, useCaseId);
        if (intent == TestIntent.VERIFICATION) {
            throw new IllegalStateException(diagnostic);
        }
        // SMOKE intent — allow but warn.
        List<String> warnings = new ArrayList<>(1);
        warnings.add("[" + intent + "] " + diagnostic);
        return warnings;
    }

    private static String formatInfeasibilityDiagnostic(
            String criterionName, FeasibilityResult r, String useCaseId) {
        return String.format(
                "%s for use case '%s' is configured with samples=%d but the resolved "
                        + "baseline rate %.4f at confidence %.4f requires at least %d "
                        + "samples to support a verification-grade claim. "
                        + "Either raise the test's sample count to ≥ %d, or declare "
                        + "the test as TestIntent.SMOKE if a lightweight check is intentional.",
                criterionName,
                useCaseId,
                r.configuredSamples(),
                r.target(),
                1.0 - r.configuredAlpha(),
                r.minimumSamples(),
                r.minimumSamples());
    }
}
