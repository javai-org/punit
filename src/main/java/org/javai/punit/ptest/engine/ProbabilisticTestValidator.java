package org.javai.punit.ptest.engine;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.spec.model.ExecutionSpecification;

/**
 * Validates probabilistic test configurations for statistical validity.
 *
 * <h2>Purpose</h2>
 * <p>This validator answers one question: <em>Does this set of inputs form a
 * valid basis for a probabilistic test?</em>
 *
 * <p>It detects conflicts between parameters and ensures the configuration
 * is unambiguous. A statistician reading this code should immediately
 * understand which combinations are valid and which are not.
 *
 * <h2>Validation Context</h2>
 * <p>Validation occurs <em>after</em> baseline selection, when we know:
 * <ul>
 *   <li>The annotation parameters</li>
 *   <li>Whether a baseline was selected (and its contents)</li>
 * </ul>
 *
 * <h2>Relationship to OperationalApproachResolver</h2>
 * <p>This validator checks <em>validity</em>. The {@link OperationalApproachResolver}
 * determines <em>which approach</em> to use and documents the tradeoffs between
 * approaches. The resolver calls this validator first; if validation fails,
 * the resolver throws with the validator's error message.
 *
 * @see OperationalApproachResolver
 */
public final class ProbabilisticTestValidator {

    /**
     * Result of validation.
     *
     * @param valid true if the configuration is valid
     * @param errors list of validation errors (empty if valid)
     */
    public record ValidationResult(
            boolean valid,
            List<String> errors
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error));
        }
    }

    /**
     * Validates a probabilistic test configuration.
     *
     * @param annotation the test annotation
     * @param selectedBaseline the selected baseline (null if none)
     * @param testName the test method name (for error messages)
     * @return validation result
     */
    public ValidationResult validate(
            ProbabilisticTest annotation,
            ExecutionSpecification selectedBaseline,
            String testName) {

        List<String> errors = new ArrayList<>();
        boolean hasBaseline = selectedBaseline != null;

        // Extract annotation parameters
        boolean hasExplicitMinPassRate = !Double.isNaN(annotation.minPassRate());
        boolean hasThresholdConfidence = !Double.isNaN(annotation.thresholdConfidence());
        boolean hasConfidenceFirstParams = isConfidenceFirstComplete(annotation);
        boolean hasPartialConfidenceFirst = isConfidenceFirstPartial(annotation);

        // ═══════════════════════════════════════════════════════════════════
        // RULE 1: Baseline + explicit minPassRate = CONFLICT (unless normative)
        // ═══════════════════════════════════════════════════════════════════
        // Statistical basis: A baseline's threshold is derived from empirical
        // observation. Overriding it discards the statistical foundation.
        //
        // EXCEPTION: If the threshold origin is a normative source (SLA, SLO, 
        // or POLICY), then the developer is intentionally overriding the 
        // baseline threshold with a documented business requirement. This is
        // allowed because the normative claim takes precedence.
        //
        // CONFLICT if: thresholdOrigin is UNSPECIFIED (likely a mistake) or 
        //              EMPIRICAL (contradictory - empirical should use baseline)
        // ═══════════════════════════════════════════════════════════════════
        if (hasBaseline && hasExplicitMinPassRate) {
            ThresholdOrigin origin = annotation.thresholdOrigin();
            boolean isNormativeOrigin = origin == ThresholdOrigin.SLA 
                    || origin == ThresholdOrigin.SLO 
                    || origin == ThresholdOrigin.POLICY;
            
            if (!isNormativeOrigin) {
                String originAdvice = origin == ThresholdOrigin.EMPIRICAL
                        ? "thresholdOrigin = EMPIRICAL is contradictory when overriding baseline."
                        : "thresholdOrigin is UNSPECIFIED, so this appears to be an accidental override.";
                        
                errors.add(String.format("""
                    CONFLICT: Baseline exists but explicit minPassRate specified.
                    
                    Test: %s
                    Baseline: %s
                    minPassRate in annotation: %.2f
                    thresholdOrigin: %s
                    
                    %s
                    
                    When a baseline exists, the threshold should be derived from it.
                    
                    If you intentionally want to override the baseline threshold with a
                    normative requirement (e.g., from an SLA), specify thresholdOrigin:
                    
                        @ProbabilisticTest(
                            useCase = ...,
                            minPassRate = %.2f,
                            thresholdOrigin = ThresholdOrigin.SLA,  // or SLO, POLICY
                            contractRef = "Reference to requirement"
                        )
                    
                    Otherwise, remove minPassRate to use the baseline's derived threshold.""",
                        testName,
                        selectedBaseline.getUseCaseId(),
                        annotation.minPassRate(),
                        origin,
                        originAdvice,
                        annotation.minPassRate()));
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // RULE 2: No baseline + no threshold = UNDEFINED
        // ═══════════════════════════════════════════════════════════════════
        // Statistical basis: Without baseline data, no empirical basis exists
        // to derive a threshold. It must be explicitly specified.
        // ═══════════════════════════════════════════════════════════════════
        if (!hasBaseline && !hasExplicitMinPassRate) {
            errors.add(String.format("""
                UNDEFINED: No threshold specified and no baseline available.
                
                Test: %s
                
                A probabilistic test requires a pass/fail threshold. Either:
                  • Reference a use case with baseline data (threshold derived)
                  • Specify minPassRate explicitly (threshold from SLA/SLO)""",
                    testName));
        }

        // ═══════════════════════════════════════════════════════════════════
        // RULE 3: thresholdConfidence requires baseline
        // ═══════════════════════════════════════════════════════════════════
        // Statistical basis: thresholdConfidence is used to derive a threshold
        // from baseline data using the Wilson score interval. Without baseline
        // data, there is nothing to derive from.
        // ═══════════════════════════════════════════════════════════════════
        if (hasThresholdConfidence && !hasBaseline) {
            errors.add(String.format("""
                INVALID: thresholdConfidence requires baseline data.
                
                Test: %s
                thresholdConfidence: %.2f
                
                The thresholdConfidence parameter derives a threshold from baseline
                data using the Wilson score interval. Without a baseline, this
                derivation is impossible.
                
                Either provide a use case with baseline data, or use minPassRate
                for explicit threshold specification.""",
                    testName,
                    annotation.thresholdConfidence()));
        }

        // ═══════════════════════════════════════════════════════════════════
        // RULE 4: Confidence-First parameters require a baseline rate
        // ═══════════════════════════════════════════════════════════════════
        // Statistical basis: Confidence-First uses power analysis to compute
        // sample size, which requires a baseline success rate as input.
        // This rate can come from either:
        //   1. A baseline/spec (empirical data from MEASURE experiment), OR
        //   2. An explicit minPassRate (normative claim from SLA/SLO)
        // ═══════════════════════════════════════════════════════════════════
        if (hasConfidenceFirstParams && !hasBaseline && !hasExplicitMinPassRate) {
            errors.add(String.format("""
                INVALID: Confidence-First approach requires a baseline rate.
                
                Test: %s
                confidence: %.2f
                minDetectableEffect: %.2f
                power: %.2f
                
                The Confidence-First approach uses power analysis to compute the
                required sample size. This requires a baseline success rate as
                input. Without a baseline rate, sample size cannot be determined.
                
                Either:
                  • Provide a use case with baseline data, OR
                  • Specify minPassRate explicitly (e.g., from an SLA/SLO)""",
                    testName,
                    annotation.confidence(),
                    annotation.minDetectableEffect(),
                    annotation.power()));
        }

        // ═══════════════════════════════════════════════════════════════════
        // RULE 5: Partial Confidence-First = INCOMPLETE
        // ═══════════════════════════════════════════════════════════════════
        // Statistical basis: Power analysis requires all three parameters
        // (confidence, effect size, power) to compute sample size.
        // ═══════════════════════════════════════════════════════════════════
        if (hasPartialConfidenceFirst) {
            List<String> present = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            
            if (!Double.isNaN(annotation.confidence())) present.add("confidence");
            else missing.add("confidence");
            
            if (!Double.isNaN(annotation.minDetectableEffect())) present.add("minDetectableEffect");
            else missing.add("minDetectableEffect");
            
            if (!Double.isNaN(annotation.power())) present.add("power");
            else missing.add("power");

            errors.add(String.format("""
                INCOMPLETE: Partial Confidence-First parameters.
                
                Test: %s
                Present: %s
                Missing: %s
                
                The Confidence-First approach requires all three parameters:
                confidence, minDetectableEffect, and power. Provide all three
                or none.""",
                    testName,
                    String.join(", ", present),
                    String.join(", ", missing)));
        }

        return errors.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure(errors);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════════════

    private boolean isConfidenceFirstComplete(ProbabilisticTest annotation) {
        return !Double.isNaN(annotation.confidence())
                && !Double.isNaN(annotation.minDetectableEffect())
                && !Double.isNaN(annotation.power());
    }

    private boolean isConfidenceFirstPartial(ProbabilisticTest annotation) {
        boolean hasConfidence = !Double.isNaN(annotation.confidence());
        boolean hasEffect = !Double.isNaN(annotation.minDetectableEffect());
        boolean hasPower = !Double.isNaN(annotation.power());

        int count = (hasConfidence ? 1 : 0) + (hasEffect ? 1 : 0) + (hasPower ? 1 : 0);
        return count > 0 && count < 3;
    }
}

