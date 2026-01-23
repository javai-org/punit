package org.javai.punit.ptest.engine;

import java.util.ArrayList;
import java.util.List;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.OperationalApproach;

/**
 * Resolves which operational approach to use based on annotation parameters.
 *
 * <p>This class enforces the mutual exclusivity of the three operational approaches
 * and provides developer-friendly error messages when conflicting parameters are specified.
 *
 * <h2>The Three Approaches</h2>
 *
 * <ol>
 *   <li><strong>Sample-Size-First</strong>: {@code samples + thresholdConfidence} → derives threshold</li>
 *   <li><strong>Confidence-First</strong>: {@code confidence + minDetectableEffect + power} → derives sample count</li>
 *   <li><strong>Threshold-First</strong>: {@code samples + minPassRate} → derives implied confidence</li>
 * </ol>
 *
 * <h2>Design Philosophy</h2>
 *
 * <p>This resolver is designed to fail fast with clear, actionable error messages.
 * When a developer provides conflicting parameters, they should immediately understand:
 * <ul>
 *   <li>What went wrong</li>
 *   <li>Which parameters conflict</li>
 *   <li>How to fix it</li>
 * </ul>
 *
 * @see OperationalApproach
 * @see ProbabilisticTest
 */
public class OperationalApproachResolver {

    private final ProbabilisticTestValidator validator;

    /**
     * Creates a resolver with a new validator instance.
     */
    public OperationalApproachResolver() {
        this(new ProbabilisticTestValidator());
    }

    /**
     * Creates a resolver with the specified validator (for testing).
     *
     * @param validator the validator to use
     */
    public OperationalApproachResolver(ProbabilisticTestValidator validator) {
        this.validator = validator;
    }

    /**
     * Result of resolving an operational approach.
     *
     * @param approach The determined operational approach (never null)
     * @param samples The sample count (may be computed for Confidence-First)
     * @param minPassRate The threshold (may be computed for Sample-Size-First)
     * @param confidence The confidence level being used
     * @param minDetectableEffect The effect size (only for Confidence-First)
     * @param power The statistical power (only for Confidence-First)
     * @param isSpecDriven True if a spec was provided, false for spec-less mode
     */
    public record ResolvedApproach(
            OperationalApproach approach,
            int samples,
            double minPassRate,
            double confidence,
            double minDetectableEffect,
            double power,
            boolean isSpecDriven
    ) {
        /**
         * Returns true if this is spec-less mode (no baseline data, explicit threshold).
         */
        public boolean isSpecless() {
            return !isSpecDriven;
        }
    }

    /**
     * Resolves the operational approach from annotation parameters.
     *
     * <p>This method validates that <strong>exactly one approach</strong> is specified.
     * Zero approaches is invalid (ambiguous intent). More than one is invalid (conflicting).
     *
     * <p>There are two modes:
     * <ul>
     *   <li><strong>Spec-driven</strong>: Uses empirical baseline data. All three approaches valid.</li>
     *   <li><strong>Spec-less</strong>: No baseline data. Only Threshold-First is valid.</li>
     * </ul>
     *
     * @param annotation The annotation to resolve
     * @param hasSpec Whether a spec reference was provided
     * @return The resolved approach with computed values
     * @throws ProbabilisticTestConfigurationException if parameters are invalid
     * @deprecated Use {@link #resolve(ProbabilisticTest, ExecutionSpecification, String)} instead
     */
    @Deprecated
    public ResolvedApproach resolve(ProbabilisticTest annotation, boolean hasSpec) {
        return resolveInternal(annotation, hasSpec);
    }

    /**
     * Resolves the operational approach from annotation parameters with full validation.
     *
     * <p>This method first validates the configuration using {@link ProbabilisticTestValidator},
     * then determines which operational approach to use.
     *
     * @param annotation The annotation to resolve
     * @param selectedBaseline The selected baseline (null if none)
     * @param testName The test method name (for error messages)
     * @return The resolved approach with computed values
     * @throws ProbabilisticTestConfigurationException if validation fails or parameters are invalid
     */
    public ResolvedApproach resolve(
            ProbabilisticTest annotation,
            ExecutionSpecification selectedBaseline,
            String testName) {
        
        // Step 1: Validate using ProbabilisticTestValidator
        ProbabilisticTestValidator.ValidationResult validation = 
                validator.validate(annotation, selectedBaseline, testName);
        
        if (!validation.valid()) {
            throw new ProbabilisticTestConfigurationException(
                    formatValidationErrors(validation.errors()));
        }
        
        // Step 2: Determine operational approach
        boolean hasSpec = selectedBaseline != null;
        return resolveInternal(annotation, hasSpec);
    }

    /**
     * Internal resolution logic (shared by both resolve methods).
     */
    private ResolvedApproach resolveInternal(ProbabilisticTest annotation, boolean hasSpec) {
        // Detect which approach parameters are set
        boolean hasSampleSizeFirst = isValidDouble(annotation.thresholdConfidence());
        boolean hasConfidenceFirst = isConfidenceFirstComplete(annotation);
        boolean hasThresholdFirst = isValidDouble(annotation.minPassRate());
        
        // Check for partial Confidence-First (common mistake)
        // Note: Also validated by ProbabilisticTestValidator, but kept here for
        // backward compatibility with the deprecated resolve(annotation, hasSpec) method
        if (isConfidenceFirstPartial(annotation)) {
            throw createPartialConfidenceFirstError(annotation);
        }

        // Count how many approaches are active
        int activeApproaches = countTrue(hasSampleSizeFirst, hasConfidenceFirst, hasThresholdFirst);
        
        // RULE: Exactly one approach must be specified
        if (activeApproaches == 0) {
            throw createNoApproachError(hasSpec);
        }
        
        if (activeApproaches > 1) {
            throw createConflictingApproachesError(hasSampleSizeFirst, hasConfidenceFirst, hasThresholdFirst);
        }
        
        // Exactly one approach specified
        if (hasSpec) {
            // Spec-driven mode: all three approaches are valid
            return resolveSpecDrivenMode(annotation, hasSampleSizeFirst, hasConfidenceFirst);
        } else {
            // Spec-less mode: only Threshold-First is valid
            return resolveSpeclessMode(annotation, hasSampleSizeFirst, hasConfidenceFirst, hasThresholdFirst);
        }
    }

    /**
     * Formats validation errors into a single error message.
     */
    private String formatValidationErrors(List<String> errors) {
        if (errors.size() == 1) {
            return errors.get(0);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Multiple validation errors:\n\n");
        for (int i = 0; i < errors.size(); i++) {
            sb.append("─── Error ").append(i + 1).append(" ───\n");
            sb.append(errors.get(i));
            if (i < errors.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * Resolves spec-driven mode (baseline data available).
     * All three approaches are valid.
     */
    private ResolvedApproach resolveSpecDrivenMode(
            ProbabilisticTest annotation,
            boolean hasSampleSizeFirst,
            boolean hasConfidenceFirst) {
        
        if (hasSampleSizeFirst) {
            return createSampleSizeFirstApproach(annotation);
        }
        if (hasConfidenceFirst) {
            return createConfidenceFirstApproach(annotation);
        }
        // hasThresholdFirst
        return createThresholdFirstApproach(annotation);
    }

    /**
     * Resolves spec-less mode (no baseline data).
     * Only Threshold-First is valid — other approaches require baseline data.
     */
    private ResolvedApproach resolveSpeclessMode(
            ProbabilisticTest annotation,
            boolean hasSampleSizeFirst,
            boolean hasConfidenceFirst,
            boolean hasThresholdFirst) {
        
        // Sample-Size-First and Confidence-First require baseline data
        if (hasSampleSizeFirst || hasConfidenceFirst) {
            throw createApproachRequiresSpecError(hasSampleSizeFirst, hasConfidenceFirst);
        }
        
        // Threshold-First is valid in spec-less mode
        if (hasThresholdFirst) {
            return createSpeclessThresholdFirstApproach(annotation);
        }
        
        // Should not reach here (activeApproaches == 1 and none matched)
        throw new IllegalStateException("Unexpected state: no approach matched despite activeApproaches == 1");
    }

    /**
     * Creates a Threshold-First approach result for spec-less mode.
     */
    private ResolvedApproach createSpeclessThresholdFirstApproach(ProbabilisticTest annotation) {
        return new ResolvedApproach(
                OperationalApproach.THRESHOLD_FIRST,
                annotation.samples(),
                annotation.minPassRate(),
                Double.NaN, // No confidence derivation without spec
                Double.NaN,
                Double.NaN,
                false // Not spec-driven
        );
    }

    private ResolvedApproach createSampleSizeFirstApproach(ProbabilisticTest annotation) {
        return new ResolvedApproach(
                OperationalApproach.SAMPLE_SIZE_FIRST,
                annotation.samples(),
                Double.NaN, // Will be derived from spec
                annotation.thresholdConfidence(),
                Double.NaN,
                Double.NaN,
                true
        );
    }

    private ResolvedApproach createConfidenceFirstApproach(ProbabilisticTest annotation) {
        return new ResolvedApproach(
                OperationalApproach.CONFIDENCE_FIRST,
                -1, // Will be computed via power analysis
                Double.NaN, // Will be derived from spec
                annotation.confidence(),
                annotation.minDetectableEffect(),
                annotation.power(),
                true
        );
    }

    private ResolvedApproach createThresholdFirstApproach(ProbabilisticTest annotation) {
        return new ResolvedApproach(
                OperationalApproach.THRESHOLD_FIRST,
                annotation.samples(),
                annotation.minPassRate(),
                Double.NaN, // Will be computed (implied confidence)
                Double.NaN,
                Double.NaN,
                true
        );
    }

    // ==================== Validation Helpers ====================

    private boolean isValidDouble(double value) {
        return !Double.isNaN(value);
    }

    private boolean isConfidenceFirstComplete(ProbabilisticTest annotation) {
        return isValidDouble(annotation.confidence())
                && isValidDouble(annotation.minDetectableEffect())
                && isValidDouble(annotation.power());
    }

    private boolean isConfidenceFirstPartial(ProbabilisticTest annotation) {
        boolean hasConfidence = isValidDouble(annotation.confidence());
        boolean hasEffect = isValidDouble(annotation.minDetectableEffect());
        boolean hasPower = isValidDouble(annotation.power());
        
        int count = countTrue(hasConfidence, hasEffect, hasPower);
        return count > 0 && count < 3;
    }

    private int countTrue(boolean... values) {
        int count = 0;
        for (boolean v : values) {
            if (v) count++;
        }
        return count;
    }

    // ==================== Error Message Factory ====================

    private ProbabilisticTestConfigurationException createNoApproachError(boolean hasSpec) {
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
            // Spec-less mode: only Threshold-First is available
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

    private ProbabilisticTestConfigurationException createConflictingApproachesError(
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

    private ProbabilisticTestConfigurationException createPartialConfidenceFirstError(ProbabilisticTest annotation) {
        List<String> missing = new ArrayList<>();
        if (!isValidDouble(annotation.confidence())) missing.add("confidence");
        if (!isValidDouble(annotation.minDetectableEffect())) missing.add("minDetectableEffect");
        if (!isValidDouble(annotation.power())) missing.add("power");
        
        List<String> present = new ArrayList<>();
        if (isValidDouble(annotation.confidence())) present.add("confidence");
        if (isValidDouble(annotation.minDetectableEffect())) present.add("minDetectableEffect");
        if (isValidDouble(annotation.power())) present.add("power");
        
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

    private ProbabilisticTestConfigurationException createApproachRequiresSpecError(
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

    private String formatActiveApproaches(List<String> approaches) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < approaches.size(); i++) {
            sb.append("      ").append(i + 1).append(". ").append(approaches.get(i));
            if (i < approaches.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }
}

