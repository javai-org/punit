package org.javai.punit.examples;

import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ProbabilisticTestBudget;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.examples.shopping.usecase.MockShoppingAssistant;
import org.javai.punit.examples.shopping.usecase.ShoppingUseCase;
import org.javai.punit.model.UseCaseOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

/**
 * Demonstrates the THREE OPERATIONAL APPROACHES for SLA-driven probabilistic testing.
 *
 * <h2>SLA-Driven Testing</h2>
 * <p>When your success threshold comes from an external requirement—a Service Level Agreement
 * (SLA), Service Level Objective (SLO), or organizational policy—you have a clear target.
 * This is <b>SLA-driven testing</b>.
 *
 * <h2>The Three Operational Approaches</h2>
 * <p>Regardless of where your threshold comes from, you must decide <b>how to parameterize</b>
 * your test. There are three approaches, demonstrated in this class:
 *
 * <ol>
 *   <li><b>Sample-Size-First (Cost-Driven)</b> — Fix the number of samples based on budget/time
 *       constraints. PUnit computes the implied confidence and detection power.</li>
 *   <li><b>Confidence-First (Risk-Driven)</b> — Fix the confidence and power requirements.
 *       PUnit computes the required sample size.</li>
 *   <li><b>Threshold-First (Baseline-Anchored)</b> — Use an exact threshold. PUnit computes
 *       the implied confidence and warns if false positive risk is high.</li>
 * </ol>
 *
 * <h2>IMPORTANT: Choose ONE Approach</h2>
 * <p>In practice, an organization will typically adopt <b>one approach</b> as their standard.
 * We show all three here purely for demonstration purposes. The right choice depends on your
 * operational priorities:
 *
 * <table border="1">
 *   <tr><th>Priority</th><th>Recommended Approach</th></tr>
 *   <tr><td>Controlling costs (CI time, API calls)</td><td>Sample-Size-First</td></tr>
 *   <tr><td>Minimizing risk (safety-critical systems)</td><td>Confidence-First</td></tr>
 *   <tr><td>Learning the trade-offs (new to PUnit)</td><td>Threshold-First</td></tr>
 * </table>
 *
 * <h2>Scenario</h2>
 * <p>Our e-commerce company has an SLA with customers guaranteeing that the shopping assistant
 * returns valid responses at least 95% of the time. This threshold comes from the business
 * contract, not from measurement. We use {@link ThresholdOrigin#SLA} to document this provenance.
 *
 * @see org.javai.punit.api.ThresholdOrigin
 * @see <a href="../../../../../../../docs/OPERATIONAL-FLOW.md">OPERATIONAL-FLOW.md</a>
 */
@Disabled("Example - demonstrates the three operational approaches for SLA-driven testing")
@ProbabilisticTestBudget(
		tokenBudget = 50000,
		timeBudgetMs = 120000,  // 2 minutes for entire class
		onBudgetExhausted = BudgetExhaustedBehavior.EVALUATE_PARTIAL
)
class ShoppingAssistantSlaExample {

	// ═══════════════════════════════════════════════════════════════════════════
	// THE CONTRACTUAL THRESHOLD
	// ═══════════════════════════════════════════════════════════════════════════
	//
	// Our SLA states: "The shopping assistant must return valid responses
	// at least 95% of the time."
	//
	// This is a NORMATIVE claim — a business requirement — not an empirical measurement.
	// We encode it directly as minPassRate = 0.95.
	//
	// ═══════════════════════════════════════════════════════════════════════════

	private static final double SLA_THRESHOLD = 0.95;
	private static final String CONTRACT_REFERENCE = "E-Commerce Platform SLA v2.1 §4.3";

	private ShoppingUseCase useCase;

	@BeforeEach
	void setUp() {
		useCase = new ShoppingUseCase(new MockShoppingAssistant());
		useCase.setModel("gpt-4");
		useCase.setTemperature(0.7);
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// APPROACH 1: SAMPLE-SIZE-FIRST (Cost-Driven)
	// ═══════════════════════════════════════════════════════════════════════════
	//
	// WHEN TO USE:
	//   • You have a fixed budget for testing (time, API calls, CI minutes)
	//   • You're running tests frequently (CI on every commit)
	//   • You're testing against rate-limited APIs
	//
	// THE QUESTION:
	//   "We can afford 100 samples. Given our 95% SLA threshold, what confidence
	//    does that achieve?"
	//
	// WHAT HAPPENS:
	//   • PUnit runs exactly 100 samples
	//   • Counts successes and failures
	//   • Computes the implied confidence and detection power
	//   • Reports what conclusions the evidence supports
	//
	// TRADE-OFF:
	//   You accept whatever statistical power 100 samples provides. This is
	//   excellent for catching large regressions but may miss small degradations.
	//
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * Sample-Size-First approach: Fix the sample count, accept implied confidence.
	 *
	 * <p>This is the most common approach for organizations with cost constraints.
	 * We specify exactly how many samples to run (100) and let PUnit compute
	 * what confidence that achieves against our 95% SLA threshold.
	 *
	 * <p>With 100 samples at a 95% threshold:
	 * <ul>
	 *   <li>We can reliably detect drops from 95% to ~85% or worse</li>
	 *   <li>Smaller degradations (e.g., 95% → 92%) may go undetected</li>
	 *   <li>Test completes quickly (~100 API calls)</li>
	 * </ul>
	 *
	 * <p><b>Best for:</b> Continuous monitoring, CI pipelines, rate-limited APIs.
	 */
	@ProbabilisticTest(
			samples = 100,                      // Fixed by budget: "We can afford 100 samples"
			minPassRate = SLA_THRESHOLD,        // 95% from SLA
			thresholdOrigin = ThresholdOrigin.SLA,
			contractRef = CONTRACT_REFERENCE,
			transparentStats = true
	)
	@Pacing(maxRequestsPerMinute = 30)         // Respect API rate limits
	void sampleSizeFirst_costDrivenApproach(TokenChargeRecorder tokenRecorder) {
		UseCaseOutcome outcome = useCase.searchProducts("wireless headphones");
		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert using the bundled success criteria from the use case
		outcome.assertAll();
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// APPROACH 2: CONFIDENCE-FIRST (Risk-Driven)
	// ═══════════════════════════════════════════════════════════════════════════
	//
	// WHEN TO USE:
	//   • You're testing safety-critical or compliance-critical systems
	//   • You need guaranteed statistical properties (for auditors, regulators)
	//   • False negatives (missing real problems) are costly
	//   • Pre-release assurance testing
	//
	// THE QUESTION:
	//   "We need 95% confidence that a pass is meaningful, and 80% probability
	//    of detecting a drop from 95% to 93%. How many samples?"
	//
	// WHAT HAPPENS:
	//   • PUnit computes the required sample size from statistical parameters
	//   • Runs that many samples (may be large)
	//   • Provides the specified confidence and power guarantees
	//
	// TRADE-OFF:
	//   Sample size is determined by statistics, not budget. For tight thresholds
	//   and high confidence, this may require many samples.
	//
	// KEY PARAMETER - minDetectableEffect:
	//   Without this, the question "how many samples to verify p ≥ 95%?" has no
	//   finite answer. We must specify the smallest violation worth detecting:
	//   - minDetectableEffect = 0.02 means "detect drops to 93% or worse"
	//   - Smaller effects require more samples; infinitesimal effects require
	//     infinite samples.
	//
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * Confidence-First approach: Fix statistical guarantees, compute sample size.
	 *
	 * <p>This approach is for organizations where risk tolerance drives decisions.
	 * We specify the confidence and power we require, plus the smallest degradation
	 * worth detecting, and PUnit computes how many samples that requires.
	 *
	 * <p>With these parameters:
	 * <ul>
	 *   <li>95% confidence: Only 5% chance of false positives</li>
	 *   <li>80% power: 80% chance of detecting a real problem</li>
	 *   <li>2% detectable effect: Will catch drops from 95% → 93%</li>
	 * </ul>
	 *
	 * <p><b>Best for:</b> Safety-critical systems, compliance audits, pre-release testing.
	 */
	@ProbabilisticTest(
			minPassRate = SLA_THRESHOLD,        // 95% from SLA
			confidence = 0.95,                  // 95% confidence level
			power = 0.80,                       // 80% detection probability
			minDetectableEffect = 0.02,         // Detect drops of 2% or more
			thresholdOrigin = ThresholdOrigin.SLA,
			contractRef = CONTRACT_REFERENCE,
			transparentStats = true
	)
	@Pacing(maxRequestsPerMinute = 30)
	void confidenceFirst_riskDrivenApproach(TokenChargeRecorder tokenRecorder) {
		// Note: PUnit computes the required sample size based on the statistical
		// parameters above. This test may run significantly more samples than
		// the sample-size-first approach.

		UseCaseOutcome outcome = useCase.searchProducts("laptop stand");
		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert using the bundled success criteria
		outcome.assertAll();
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// APPROACH 3: THRESHOLD-FIRST (Direct Threshold)
	// ═══════════════════════════════════════════════════════════════════════════
	//
	// WHEN TO USE:
	//   • You want to test against the exact SLA threshold as written
	//   • You're learning PUnit and want to see the trade-offs explicitly
	//   • You deliberately accept strict thresholds knowing the false positive risk
	//
	// THE QUESTION:
	//   "Our SLA says 95%. Test against exactly that."
	//
	// WHAT HAPPENS:
	//   • PUnit uses the explicit threshold
	//   • Computes the implied confidence
	//   • Warns if false positive rate is high
	//
	// TRADE-OFF:
	//   With small sample sizes, tests may fail due to sampling variance alone.
	//   You're essentially asking: "Does this sample meet exactly 95%?"
	//   — and with few samples, variance makes this hard to answer reliably.
	//
	// WHEN IT MAKES SENSE:
	//   This approach is useful when learning PUnit, as it makes the statistical
	//   penalties very visible. In production, most organizations choose
	//   Approach 1 or 2 instead.
	//
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * Threshold-First approach: Use exact threshold, accept statistical consequences.
	 *
	 * <p>This approach uses an explicit threshold value. When combined with
	 * transparent statistics mode, PUnit will show exactly what confidence
	 * level the test achieves—and warn if false positive rates are concerning.
	 *
	 * <p>Useful for:
	 * <ul>
	 *   <li>Learning the trade-offs between samples, threshold, and confidence</li>
	 *   <li>Organizations deliberately accepting strict thresholds</li>
	 *   <li>Situations where the threshold is non-negotiable (SLA as written)</li>
	 * </ul>
	 *
	 * <p><b>Note:</b> With small samples against a high threshold, tests may fail
	 * due to sampling variance. This is not a bug—it's statistics. Consider
	 * using Approach 1 with more samples for production use.
	 */
	@ProbabilisticTest(
			samples = 50,                       // Small sample for demonstration
			minPassRate = SLA_THRESHOLD,        // Exact threshold from SLA
			thresholdOrigin = ThresholdOrigin.SLA,
			contractRef = CONTRACT_REFERENCE,
			transparentStats = true             // Show the statistical analysis
	)
	@Pacing(maxRequestsPerMinute = 30)
	void thresholdFirst_baselineAnchoredApproach(TokenChargeRecorder tokenRecorder) {
		// With only 50 samples against a 95% threshold, PUnit will report:
		// - The observed pass rate
		// - The implied confidence level
		// - Whether the evidence supports the SLA claim
		//
		// Transparent stats mode shows the full calculation, helping you
		// understand the trade-offs.

		UseCaseOutcome outcome = useCase.searchProducts("USB-C hub");
		tokenRecorder.recordTokens(outcome.result().getInt("tokensUsed", 0));

		// Assert using the bundled success criteria
		outcome.assertAll();
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// CHOOSING YOUR APPROACH
	// ═══════════════════════════════════════════════════════════════════════════
	//
	// In practice, pick ONE approach and use it consistently:
	//
	// ┌──────────────────────────────────────────────────────────────────────────┐
	// │ IF YOUR PRIORITY IS...           │ USE...                               │
	// ├──────────────────────────────────────────────────────────────────────────┤
	// │ Controlling costs                │ Sample-Size-First                    │
	// │   (CI time, API calls)           │   → Fix samples, compute confidence  │
	// ├──────────────────────────────────────────────────────────────────────────┤
	// │ Minimizing risk                  │ Confidence-First                     │
	// │   (safety-critical, compliance)  │   → Fix confidence, compute samples  │
	// ├──────────────────────────────────────────────────────────────────────────┤
	// │ Learning the trade-offs          │ Threshold-First + transparentStats   │
	// │   (new to PUnit)                 │   → See exactly what's happening     │
	// └──────────────────────────────────────────────────────────────────────────┘
	//
	// ═══════════════════════════════════════════════════════════════════════════
}

