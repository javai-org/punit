package org.javai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ThresholdDeriver}.
 * 
 * <h2>Statistical Background</h2>
 * <p>Threshold derivation accounts for:
 * <ul>
 *   <li>Uncertainty in the baseline estimate (it's an estimate, not truth)</li>
 *   <li>Increased variance with smaller test samples</li>
 *   <li>Desired false positive rate (α = 1 - confidence)</li>
 * </ul>
 * 
 * <h2>Key Test Cases</h2>
 * <ul>
 *   <li>Sample-Size-First approach (given n and α, derive threshold)</li>
 *   <li>Threshold-First approach (given threshold, derive implied confidence)</li>
 *   <li>Perfect baseline handling (p̂ = 1)</li>
 *   <li>Statistical soundness flags</li>
 * </ul>
 */
@DisplayName("ThresholdDeriver")
class ThresholdDeriverTest {
    
    private ThresholdDeriver deriver;
    
    @BeforeEach
    void setUp() {
        deriver = new ThresholdDeriver();
    }
    
    @Nested
    @DisplayName("Sample-Size-First Approach (Cost-Driven)")
    class SampleSizeFirstApproach {
        
        @Test
        @DisplayName("derives threshold from baseline data")
        void derivesThresholdFromBaselineData() {
            // Baseline: 951 successes out of 1000 trials (95.1%)
            // Test: 100 samples at 95% confidence
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);
            
            // Threshold should be the Wilson lower bound ≈ 93.6%
            assertThat(threshold.value()).isCloseTo(0.936, within(0.01));
            assertThat(threshold.approach()).isEqualTo(OperationalApproach.SAMPLE_SIZE_FIRST);
            assertThat(threshold.isStatisticallySound()).isTrue();
        }
        
        @Test
        @DisplayName("threshold is lower than baseline rate")
        void thresholdIsLowerThanBaselineRate() {
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);
            
            // Gap accounts for sampling uncertainty
            assertThat(threshold.value()).isLessThan(0.951);
            assertThat(threshold.gapFromBaseline()).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("higher confidence produces lower threshold")
        void higherConfidenceProducesLowerThreshold() {
            DerivedThreshold threshold95 = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);
            DerivedThreshold threshold99 = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.99);
            
            // 99% confidence → more conservative threshold
            assertThat(threshold99.value()).isLessThan(threshold95.value());
        }
        
        @Test
        @DisplayName("larger baseline sample produces higher threshold (more precise)")
        void largerBaselineSampleProducesHigherThreshold() {
            // Same observed rate (95%), different baseline sample sizes
            DerivedThreshold smallBaseline = deriver.deriveSampleSizeFirst(
                100, 95, 50, 0.95);
            DerivedThreshold largeBaseline = deriver.deriveSampleSizeFirst(
                10000, 9500, 50, 0.95);
            
            // Larger baseline → narrower CI → higher lower bound
            assertThat(largeBaseline.value()).isGreaterThan(smallBaseline.value());
        }
        
        @Test
        @DisplayName("handles perfect baseline (100% success) correctly")
        void handlesPerfectBaseline() {
            // All 1000 trials succeeded
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 1000, 100, 0.95);
            
            // Should NOT be 1.0 (that would cause every failure to fail)
            assertThat(threshold.value()).isLessThan(1.0);
            
            // Wilson one-sided lower bound for 1000/1000 at 95% ≈ 99.73%
            assertThat(threshold.value()).isCloseTo(0.9973, within(0.001));
        }
        
        @Test
        @DisplayName("context captures derivation parameters")
        void contextCapturesDerivationParameters() {
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);
            
            DerivationContext context = threshold.context();
            assertThat(context.baselineRate()).isCloseTo(0.951, within(0.001));
            assertThat(context.baselineSamples()).isEqualTo(1000);
            assertThat(context.testSamples()).isEqualTo(100);
            assertThat(context.confidence()).isEqualTo(0.95);
        }
    }
    
    @Nested
    @DisplayName("Threshold-First Approach (Baseline-Anchored)")
    class ThresholdFirstApproach {
        
        @Test
        @DisplayName("computes implied confidence for given threshold")
        void computesImpliedConfidenceForGivenThreshold() {
            // Baseline: 951/1000 (95.1%)
            // Explicit threshold: 93% (slightly below the 95% CI lower bound of ~93.6%)
            DerivedThreshold result = deriver.deriveThresholdFirst(
                1000, 951, 100, 0.93);
            
            // Threshold should be exactly what was specified
            assertThat(result.value()).isEqualTo(0.93);
            assertThat(result.approach()).isEqualTo(OperationalApproach.THRESHOLD_FIRST);
            
            // Implied confidence should be high (threshold slightly below the 95% CI lower bound)
            // Since the Wilson lower bound at 95% is about 93.6%, 
            // a threshold of 93% implies a confidence level around 97-99%
            assertThat(result.context().confidence()).isGreaterThan(0.90);
            assertThat(result.isStatisticallySound()).isTrue();
        }
        
        @Test
        @DisplayName("flags threshold at baseline rate as statistically unsound")
        void flagsThresholdAtBaselineAsUnsound() {
            // Setting threshold = baseline rate is statistically unwise
            // Implied confidence ≈ 50% (coin flip)
            DerivedThreshold result = deriver.deriveThresholdFirst(
                1000, 951, 100, 0.951);
            
            // Should be flagged as unsound
            assertThat(result.isStatisticallySound()).isFalse();
            assertThat(result.context().confidence()).isLessThan(0.8);
        }
        
        @Test
        @DisplayName("flags threshold above baseline rate as statistically unsound")
        void flagsThresholdAboveBaselineAsUnsound() {
            // Threshold above baseline: guaranteed to fail often
            DerivedThreshold result = deriver.deriveThresholdFirst(
                1000, 900, 100, 0.95); // baseline = 90%, threshold = 95%
            
            assertThat(result.isStatisticallySound()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Input Validation")
    class InputValidation {
        
        @Test
        @DisplayName("rejects non-positive baseline samples")
        void rejectsNonPositiveBaselineSamples() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(0, 0, 100, 0.95))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Baseline samples must be positive");
        }
        
        @Test
        @DisplayName("rejects baseline successes exceeding samples")
        void rejectsBaselineSuccessesExceedingSamples() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 1100, 100, 0.95))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("rejects non-positive test samples")
        void rejectsNonPositiveTestSamples() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 951, 0, 0.95))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test samples must be positive");
        }
        
        @Test
        @DisplayName("rejects confidence outside (0, 1)")
        void rejectsInvalidConfidence() {
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 951, 100, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence");
            
            assertThatThrownBy(() -> deriver.deriveSampleSizeFirst(1000, 951, 100, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence");
        }
        
        @Test
        @DisplayName("rejects explicit threshold outside [0, 1]")
        void rejectsInvalidExplicitThreshold() {
            assertThatThrownBy(() -> deriver.deriveThresholdFirst(1000, 951, 100, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explicit threshold");
            
            assertThatThrownBy(() -> deriver.deriveThresholdFirst(1000, 951, 100, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Explicit threshold");
        }
    }
    
    @Nested
    @DisplayName("Worked Example from STATISTICAL-COMPANION")
    class WorkedExample {
        
        @Test
        @DisplayName("baseline experiment: 951/1000 with 100-sample test")
        void baselineExperimentWorkedExample() {
            // From STATISTICAL-COMPANION document:
            // Baseline: 951 successes in 1000 trials (p̂ = 0.951)
            // Test samples: 100
            // Confidence: 95%
            
            DerivedThreshold threshold = deriver.deriveSampleSizeFirst(
                1000, 951, 100, 0.95);
            
            // The document states threshold should be around 93-94%
            // This accounts for:
            // 1. Uncertainty in baseline (Wilson lower bound)
            // 2. 95% confidence level
            assertThat(threshold.value())
                .as("Threshold for 951/1000 baseline at 95% confidence")
                .isCloseTo(0.936, within(0.01));
            
            // Gap from baseline should be meaningful
            assertThat(threshold.gapFromBaseline())
                .as("Gap from baseline")
                .isGreaterThan(0.01)
                .isLessThan(0.05);
        }
    }
}

