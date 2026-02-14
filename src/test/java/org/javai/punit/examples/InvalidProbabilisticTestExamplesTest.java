package org.javai.punit.examples;

import org.javai.punit.api.ProbabilisticTest;
import org.junit.jupiter.api.Disabled;

/**
 * Quick-fail examples: invalid {@code @ProbabilisticTest} configurations.
 *
 * <p>Each method below is <em>intentionally misconfigured</em>. PUnit must reject it
 * <strong>before executing any samples</strong> (i.e., the method body must never run).
 *
 * <h2>Glossary (annotation attributes referenced below)</h2>
 * <ul>
 *   <li><b>samples</b>: planned number of trials (sample size {@code n}).</li>
 *   <li><b>minPassRate</b>: explicit pass-rate threshold (probability in {@code [0,1]}).</li>
 *   <li><b>thresholdConfidence</b>: confidence used to <em>derive</em> a threshold from a baseline (Sample-Size-First).</li>
 *   <li><b>confidence</b>: confidence level {@code 1-α} used for inference/planning (must be in {@code (0,1)}).</li>
 *   <li><b>power</b>: {@code 1-β}; probability of detecting a real degradation of at least {@code minDetectableEffect}.</li>
 *   <li><b>minDetectableEffect</b>: smallest degradation worth detecting (used for power/sample-size planning).</li>
 *   <li><b>spec</b> / use-case / baseline reference: required when PUnit must consult baseline data.</li>
 * </ul>
 *
 * <h2>Pedagogical ordering</h2>
 * Start with “obviously incomplete” configs a Java dev might write on day one, then move to
 * feasibility, then over-specification, then spec requirements, and finally pure range hygiene.
 */
@Disabled("Intentionally invalid configurations — documentation and manual sanity-checking only")
class InvalidProbabilisticTestExamplesTest {

	// ═══════════════════════════════════════════════════════════════════════════
	// 1) NO APPROACH SPECIFIED (common newcomer mistake)
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * <b>Error:</b> No operational approach is selected.
	 *
	 * <p><b>What it says:</b> “Run 100 samples.”</p>
	 * <p><b>Why invalid:</b> A probabilistic test needs <em>some</em> notion of threshold/confidence/spec
	 * to define what “pass” even means. {@code samples} alone is just a loop count.</p>
	 *
	 * <p><b>Caught by:</b> Resolver check {@code activeApproaches == 0}.</p>
	 * <p><b>Fix:</b> Choose one approach, e.g. {@code samples + minPassRate} (Threshold-First),
	 * or {@code samples + thresholdConfidence} (Sample-Size-First, with spec).</p>
	 */
	@ProbabilisticTest(samples = 100)
	void noApproach_samplesOnly() {
		// body never executed
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// 2) INCOMPLETE CONFIDENCE-FIRST (common “almost right” mistake)
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * <b>Error:</b> Partial Confidence-First planning parameters.
	 *
	 * <p><b>What it says:</b> “Use 99% confidence and 80% power.”</p>
	 * <p><b>Why invalid:</b> Confidence-First requires <b>three</b> parameters:
	 * {@code confidence + power + minDetectableEffect}. Without {@code minDetectableEffect},
	 * PUnit cannot compute a finite/meaningful required sample size.</p>
	 *
	 * <p><b>Caught by:</b> Rule 6 / resolver “partial Confidence-First” check.</p>
	 * <p><b>Fix:</b> Add {@code minDetectableEffect} (and typically remove {@code samples}, letting PUnit compute it),
	 * or remove {@code confidence}/{@code power} and use Threshold-First instead.</p>
	 */
	@ProbabilisticTest(confidence = 0.99, power = 0.80) // forgot minDetectableEffect
	void incompleteConfidenceFirst_missingMinDetectableEffect() {
		// body never executed
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// 3) FEASIBILITY (why tiny N cannot “verify” ambitious thresholds)
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * <b>Error:</b> VERIFICATION intent with an infeasibly small sample size.
	 *
	 * <p><b>What it says:</b> “Verify 95% reliability with 5 samples.”</p>
	 * <p><b>Why invalid:</b> With default confidence (α = 0.05), the minimum feasible N for
	 * {@code minPassRate = 0.95} is about 52 (Wilson lower bound feasibility gate).
	 * Five samples cannot support an evidential PASS verdict.</p>
	 *
	 * <p><b>Caught by:</b> VERIFICATION feasibility gate.</p>
	 * <p><b>Fix:</b> Increase {@code samples} to at least the required minimum, or switch to SMOKE intent.</p>
	 */
	@ProbabilisticTest(samples = 5, minPassRate = 0.95)
	void infeasibleVerification() {
		// body never executed
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// 4) OVER-SPECIFICATION (multiple “knobs” pinned simultaneously)
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * <b>Error:</b> Over-specified configuration.
	 *
	 * <p><b>What it says:</b> Threshold-First ({@code minPassRate}) <em>and</em> a confidence requirement ({@code confidence}).</p>
	 * <p><b>Why invalid:</b> Once you pin a threshold explicitly, adding a confidence requirement is a second,
	 * incompatible “knob” unless you switch fully to Confidence-First planning (which then requires
	 * {@code power + minDetectableEffect} and typically omits {@code samples}).</p>
	 *
	 * <p><b>Caught by:</b> {@code isOverSpecified()} guard.</p>
	 * <p><b>Fix:</b> Either remove {@code confidence} (pure Threshold-First), or use full Confidence-First planning
	 * ({@code confidence + power + minDetectableEffect}) and omit {@code minPassRate} if the threshold is to be
	 * derived from baseline.</p>
	 */
	@ProbabilisticTest(confidence = 0.99, samples = 10, minPassRate = 0.9999)
	void approachMixing_thresholdFirstPlusConfidence() {
		// body never executed
	}

	/**
	 * <b>Error:</b> Over-specified configuration.
	 *
	 * <p><b>What it says:</b> Sample-Size-First ({@code thresholdConfidence}) <em>and</em> Threshold-First ({@code minPassRate}).</p>
	 * <p><b>Why invalid:</b> You are selecting two different approaches to define the threshold at once:
	 * one derived from a baseline (via {@code thresholdConfidence}) and one explicit (via {@code minPassRate}).</p>
	 *
	 * <p><b>Caught by:</b> {@code isOverSpecified()} guard.</p>
	 * <p><b>Fix:</b> Keep <em>either</em> {@code samples + thresholdConfidence} (plus spec) <em>or</em>
	 * {@code samples + minPassRate}.</p>
	 */
	@ProbabilisticTest(thresholdConfidence = 0.95, minPassRate = 0.90, samples = 50)
	void conflictingApproaches_twoActiveApproaches() {
		// body never executed
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// 5) APPROACH REQUIRES SPEC (baseline-derived modes need baseline context)
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * <b>Error:</b> Sample-Size-First without a spec/baseline.
	 *
	 * <p><b>Why invalid:</b> {@code thresholdConfidence} means “derive the threshold from baseline data.”
	 * With no spec, there is no baseline to consult.</p>
	 *
	 * <p><b>Caught by:</b> Resolver specless-mode guard.</p>
	 * <p><b>Fix:</b> Add a spec/use-case, or use Threshold-First ({@code samples + minPassRate}).</p>
	 */
	@ProbabilisticTest(thresholdConfidence = 0.95)
	void sampleSizeFirstWithoutSpec() {
		// body never executed
	}

	/**
	 * <b>Error:</b> Confidence-First without a spec/baseline.
	 *
	 * <p><b>Why invalid:</b> Power analysis requires a baseline (or equivalent reference) to plan sample size.
	 * With no spec, the required N cannot be computed meaningfully.</p>
	 *
	 * <p><b>Caught by:</b> Resolver specless-mode guard.</p>
	 * <p><b>Fix:</b> Add a spec/use-case, or use Threshold-First ({@code samples + minPassRate}).</p>
	 */
	@ProbabilisticTest(confidence = 0.99, minDetectableEffect = 0.05, power = 0.80)
	void confidenceFirstWithoutSpec() {
		// body never executed
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// 6) RANGE VIOLATIONS (pure hygiene)
	// ═══════════════════════════════════════════════════════════════════════════

	/**
	 * <b>Error:</b> {@code confidence = 1.0} is outside the valid range {@code (0, 1)}.
	 *
	 * <p><b>Why invalid:</b> α = 0 implies absolute certainty, which cannot be obtained from finite samples.
	 * Unless the system under test is fully deterministic, in which case use a regular JUnit {@code @Test} and not a
	 * {@code @ProbabilisticTest}</p>
	 * <p><b>Caught by:</b> Confidence range validation.</p>
	 * <p><b>Fix:</b> Use a value strictly between 0 and 1 (e.g. 0.95, 0.99).</p>
	 *
	 * <p><i>Note:</i> This example also includes {@code samples + minPassRate} because it makes the range error
	 * visually obvious; whether range validation fires before other checks is an implementation detail.</p>
	 */
	@ProbabilisticTest(confidence = 1.0, samples = 10, minPassRate = 0.5)
	void confidenceAtBoundary() {
		// body never executed
	}

	/**
	 * <b>Error:</b> {@code minPassRate = -0.1} is outside {@code [0, 1]}.
	 *
	 * <p><b>Why invalid:</b> A pass rate is a probability.</p>
	 * <p><b>Caught by:</b> minPassRate range validation.</p>
	 * <p><b>Fix:</b> Use a value in {@code [0, 1]}.</p>
	 */
	@ProbabilisticTest(confidence = 0.99, samples = 10, minPassRate = -0.1)
	void minPassRateBelowZero() {
		// body never executed
	}
}