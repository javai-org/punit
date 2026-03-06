package org.javai.punit.ptest.engine;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.ProbabilisticTest;

/**
 * Factory for developer-friendly error messages when {@code @ProbabilisticTest} configuration
 * is invalid.
 *
 * <p>Each method constructs a formatted error message with:
 * <ul>
 *   <li>What went wrong</li>
 *   <li>Why it's invalid</li>
 *   <li>How to fix it (with annotation examples)</li>
 * </ul>
 *
 * @see OperationalApproachResolver
 */
class OperationalApproachErrors {

    private OperationalApproachErrors() {}

    static ProbabilisticTestConfigurationException overSpecification(ProbabilisticTest annotation) {
        List<String> pinned = new ArrayList<>();
        pinned.add("samples = " + annotation.samples());
        if (!Double.isNaN(annotation.confidence())) {
            pinned.add("confidence = " + annotation.confidence());
        }
        if (!Double.isNaN(annotation.thresholdConfidence())) {
            pinned.add("thresholdConfidence = " + annotation.thresholdConfidence());
        }
        pinned.add("minPassRate = " + annotation.minPassRate());

        return new ProbabilisticTestConfigurationException(String.format("""

            ═══════════════════════════════════════════════════════════════════════════
            ❌ PROBABILISTIC TEST CONFIGURATION ERROR: Over-Specified
            ═══════════════════════════════════════════════════════════════════════════

            You have pinned all three key variables:

            %s

            ───────────────────────────────────────────────────────────────────────────
            WHY THIS IS INVALID
            ───────────────────────────────────────────────────────────────────────────

            Statistical hypothesis testing has a fundamental constraint: sample size,
            confidence, and threshold are linked by mathematics. You choose TWO; the
            third must be derived. Specifying all three creates an over-determined
            system that is almost certainly inconsistent.

            ───────────────────────────────────────────────────────────────────────────
            HOW TO FIX
            ───────────────────────────────────────────────────────────────────────────

            Pick ONE of the three valid approaches:

            • Sample-Size-First: keep 'samples' and 'thresholdConfidence', remove 'minPassRate'
            • Confidence-First: keep 'confidence', 'minDetectableEffect', 'power', remove 'minPassRate'
            • Threshold-First: keep 'samples' and 'minPassRate', remove confidence params

            ═══════════════════════════════════════════════════════════════════════════
            """, formatPinnedValues(pinned)));
    }

    static ProbabilisticTestConfigurationException noApproach(boolean hasSpec) {
        if (hasSpec) {
            return new ProbabilisticTestConfigurationException("""

                ═══════════════════════════════════════════════════════════════════════════
                ❌ PROBABILISTIC TEST CONFIGURATION ERROR
                ═══════════════════════════════════════════════════════════════════════════

                A spec was provided, but no operational approach was specified.

                When using a spec, you must choose ONE of the following approaches:

                ┌─────────────────────────────────────────────────────────────────────────┐
                │ APPROACH 1: Sample-Size-First (Cost-Driven)                             │
                │ ─────────────────────────────────────────────────────────────────────── │
                │ You specify: samples + thresholdConfidence                              │
                │ Framework computes: minPassRate (threshold)                             │
                │                                                                         │
                │ Example:                                                                │
                │   @ProbabilisticTest(                                                   │
                │       spec = "my-use-case:v1",                                          │
                │       samples = 100,                                                    │
                │       thresholdConfidence = 0.95                                        │
                │   )                                                                     │
                └─────────────────────────────────────────────────────────────────────────┘

                ┌─────────────────────────────────────────────────────────────────────────┐
                │ APPROACH 2: Confidence-First (Quality-Driven)                           │
                │ ─────────────────────────────────────────────────────────────────────── │
                │ You specify: confidence + minDetectableEffect + power                   │
                │ Framework computes: samples (required sample size)                      │
                │                                                                         │
                │ Example:                                                                │
                │   @ProbabilisticTest(                                                   │
                │       spec = "my-use-case:v1",                                          │
                │       confidence = 0.99,                                                │
                │       minDetectableEffect = 0.05,                                       │
                │       power = 0.80                                                      │
                │   )                                                                     │
                └─────────────────────────────────────────────────────────────────────────┘

                ┌─────────────────────────────────────────────────────────────────────────┐
                │ APPROACH 3: Threshold-First (Baseline-Anchored)                         │
                │ ─────────────────────────────────────────────────────────────────────── │
                │ You specify: samples + minPassRate                                      │
                │ Framework computes: implied confidence (with warning if unsound)        │
                │                                                                         │
                │ Example:                                                                │
                │   @ProbabilisticTest(                                                   │
                │       spec = "my-use-case:v1",                                          │
                │       samples = 100,                                                    │
                │       minPassRate = 0.951                                               │
                │   )                                                                     │
                └─────────────────────────────────────────────────────────────────────────┘

                ═══════════════════════════════════════════════════════════════════════════
                """);
        } else {
            return new ProbabilisticTestConfigurationException("""

                ═══════════════════════════════════════════════════════════════════════════
                ❌ PROBABILISTIC TEST CONFIGURATION ERROR
                ═══════════════════════════════════════════════════════════════════════════

                @ProbabilisticTest requires you to specify an operational approach.

                Without a spec, you must use Threshold-First (explicit threshold):

                ┌─────────────────────────────────────────────────────────────────────────┐
                │ THRESHOLD-FIRST (Spec-less Mode)                                        │
                │ ─────────────────────────────────────────────────────────────────────── │
                │ You specify: samples + minPassRate                                      │
                │                                                                         │
                │ Example:                                                                │
                │   @ProbabilisticTest(                                                   │
                │       samples = 100,                                                    │
                │       minPassRate = 0.95                                                │
                │   )                                                                     │
                └─────────────────────────────────────────────────────────────────────────┘

                ───────────────────────────────────────────────────────────────────────────
                💡 TIP: For statistically rigorous testing, use a spec
                ───────────────────────────────────────────────────────────────────────────

                Specs contain empirically-derived baseline data, enabling:
                  • Automatic threshold derivation (Sample-Size-First)
                  • Power analysis for sample size (Confidence-First)
                  • Statistical soundness checks (Threshold-First)

                Create a spec by running an experiment:
                  1. Define a @UseCase
                  2. Run an @Experiment to gather baseline data
                  3. Approve the baseline to create a spec
                  4. Reference the spec in your @ProbabilisticTest

                ═══════════════════════════════════════════════════════════════════════════
                """);
        }
    }

    static ProbabilisticTestConfigurationException conflictingApproaches(
            boolean hasSampleSizeFirst,
            boolean hasConfidenceFirst,
            boolean hasThresholdFirst) {

        List<String> activeApproaches = new ArrayList<>();
        if (hasSampleSizeFirst) activeApproaches.add("Sample-Size-First (thresholdConfidence)");
        if (hasConfidenceFirst) activeApproaches.add("Confidence-First (confidence + minDetectableEffect + power)");
        if (hasThresholdFirst) activeApproaches.add("Threshold-First (minPassRate)");

        return new ProbabilisticTestConfigurationException(String.format("""

            ═══════════════════════════════════════════════════════════════════════════
            ❌ PROBABILISTIC TEST CONFIGURATION ERROR: Conflicting Approaches
            ═══════════════════════════════════════════════════════════════════════════

            Multiple operational approaches were specified. You can only use ONE.

            ⚠️  ACTIVE APPROACHES DETECTED:
            %s

            ───────────────────────────────────────────────────────────────────────────
            WHY THIS MATTERS
            ───────────────────────────────────────────────────────────────────────────

            Statistical testing has a fundamental constraint: you can control TWO of
            these three variables, and the third is determined by mathematics:

              • Sample size (how many times to run the test)
              • Confidence level (how sure you are about the result)
              • Threshold (the pass/fail cutoff)

            Each approach represents a different choice about which two to control.
            Specifying parameters from multiple approaches creates a contradiction.

            ───────────────────────────────────────────────────────────────────────────
            HOW TO FIX
            ───────────────────────────────────────────────────────────────────────────

            Remove the extra parameters so only ONE approach is active:

            • For Sample-Size-First: keep 'samples' and 'thresholdConfidence' ONLY
            • For Confidence-First: keep 'confidence', 'minDetectableEffect', 'power' ONLY
            • For Threshold-First: keep 'samples' and 'minPassRate' ONLY

            ═══════════════════════════════════════════════════════════════════════════
            """, formatActiveApproaches(activeApproaches)));
    }

    static ProbabilisticTestConfigurationException partialConfidenceFirst(ProbabilisticTest annotation) {
        List<String> missing = new ArrayList<>();
        if (Double.isNaN(annotation.confidence())) missing.add("confidence");
        if (Double.isNaN(annotation.minDetectableEffect())) missing.add("minDetectableEffect");
        if (Double.isNaN(annotation.power())) missing.add("power");

        List<String> present = new ArrayList<>();
        if (!Double.isNaN(annotation.confidence())) present.add("confidence");
        if (!Double.isNaN(annotation.minDetectableEffect())) present.add("minDetectableEffect");
        if (!Double.isNaN(annotation.power())) present.add("power");

        return new ProbabilisticTestConfigurationException(String.format("""

            ═══════════════════════════════════════════════════════════════════════════
            ❌ PROBABILISTIC TEST CONFIGURATION ERROR: Incomplete Confidence-First
            ═══════════════════════════════════════════════════════════════════════════

            The Confidence-First approach requires ALL THREE parameters:
              • confidence
              • minDetectableEffect
              • power

            ✓ You provided: %s
            ✗ Missing: %s

            ───────────────────────────────────────────────────────────────────────────
            EXAMPLE (Confidence-First, complete)
            ───────────────────────────────────────────────────────────────────────────

            @ProbabilisticTest(
                spec = "my-use-case:v1",
                confidence = 0.99,           // 99%% confidence in results
                minDetectableEffect = 0.05,  // Detect 5%% degradation
                power = 0.80                 // 80%% chance of catching degradation
            )

            ═══════════════════════════════════════════════════════════════════════════
            """, String.join(", ", present), String.join(", ", missing)));
    }

    static ProbabilisticTestConfigurationException approachRequiresSpec(
            boolean hasSampleSizeFirst,
            boolean hasConfidenceFirst) {

        String approach = hasSampleSizeFirst ? "Sample-Size-First" : "Confidence-First";
        String params = hasSampleSizeFirst
                ? "thresholdConfidence"
                : "confidence + minDetectableEffect + power";
        String reason = hasSampleSizeFirst
                ? "derive a threshold from baseline data"
                : "compute required sample size via power analysis";

        return new ProbabilisticTestConfigurationException(String.format("""

            ═══════════════════════════════════════════════════════════════════════════
            ❌ PROBABILISTIC TEST CONFIGURATION ERROR
            ═══════════════════════════════════════════════════════════════════════════

            The %s approach requires a spec.

            You specified: %s
            But no spec was provided.

            ───────────────────────────────────────────────────────────────────────────
            WHY?
            ───────────────────────────────────────────────────────────────────────────

            The %s approach needs baseline data to %s.
            Baseline data comes from running an experiment and approving the results.

            ───────────────────────────────────────────────────────────────────────────
            HOW TO FIX
            ───────────────────────────────────────────────────────────────────────────

            Option 1: Add a spec (recommended for rigorous testing)

              @ProbabilisticTest(
                  spec = "my-use-case:v1",
                  %s
              )

            Option 2: Use Threshold-First (spec-less mode)

              @ProbabilisticTest(
                  samples = 100,
                  minPassRate = 0.95   // Explicit threshold, no baseline needed
              )

            ═══════════════════════════════════════════════════════════════════════════
            """, approach, params, approach, reason, params));
    }

    private static String formatPinnedValues(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            sb.append("      • ").append(value).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String formatActiveApproaches(List<String> approaches) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < approaches.size(); i++) {
            sb.append("      ").append(i + 1).append(". ").append(approaches.get(i));
            if (i < approaches.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }
}
