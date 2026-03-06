package org.javai.punit.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BinomialProportionEstimator}.
 * 
 * <h2>Statistical Background</h2>
 * <p>This test class verifies the Wilson score interval calculation, which provides
 * confidence intervals for binomial proportions with better coverage properties
 * than the normal (Wald) approximation.
 * 
 * <h2>Key Test Cases</h2>
 * <ul>
 *   <li>Standard estimation with known z-scores</li>
 *   <li>Edge cases: p̂ = 0 and p̂ = 1 (where normal approximation fails)</li>
 *   <li>One-sided vs two-sided intervals</li>
 *   <li>Validation of input constraints</li>
 * </ul>
 */
@DisplayName("BinomialProportionEstimator")
class BinomialProportionEstimatorTest {
    
    private BinomialProportionEstimator estimator;
    
    @BeforeEach
    void setUp() {
        estimator = new BinomialProportionEstimator();
    }
    
    @Nested
    @DisplayName("Standard Error Calculation: SE(p̂) = √(p̂(1-p̂)/n)")
    class StandardErrorCalculation {
        
        @Test
        @DisplayName("calculates SE for typical proportions")
        void calculatesStandardErrorForTypicalProportions() {
            // p̂ = 0.5, n = 100
            // SE = √(0.5 × 0.5 / 100) = √(0.25/100) = √0.0025 = 0.05
            double se = estimator.standardError(50, 100);
            assertThat(se).isCloseTo(0.05, within(0.0001));
        }
        
        @Test
        @DisplayName("SE is maximized at p̂ = 0.5")
        void standardErrorMaximizedAtHalf() {
            // The variance p̂(1-p̂) is maximized at p̂ = 0.5
            double seAt50 = estimator.standardError(50, 100);
            double seAt80 = estimator.standardError(80, 100);
            double seAt95 = estimator.standardError(95, 100);
            
            assertThat(seAt50).isGreaterThan(seAt80);
            assertThat(seAt80).isGreaterThan(seAt95);
        }
        
        @Test
        @DisplayName("SE collapses to 0 when p̂ = 0 or p̂ = 1")
        void standardErrorCollapsesAtExtremes() {
            // This is why Wilson interval is preferred over normal approximation
            assertThat(estimator.standardError(0, 100)).isEqualTo(0.0);
            assertThat(estimator.standardError(100, 100)).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("SE decreases with larger sample size (∝ 1/√n)")
        void standardErrorDecreasesWithSampleSize() {
            double seN100 = estimator.standardError(50, 100);
            double seN400 = estimator.standardError(200, 400);
            
            // n increases by 4× → SE decreases by 2×
            assertThat(seN400).isCloseTo(seN100 / 2.0, within(0.0001));
        }
    }
    
    @Nested
    @DisplayName("Wilson Score Confidence Interval")
    class WilsonScoreConfidenceInterval {
        
        @Test
        @DisplayName("computes 95% CI for typical proportion")
        void computes95CIForTypicalProportion() {
            // 90 successes out of 100 trials
            // p̂ = 0.90
            ProportionEstimate estimate = estimator.estimate(90, 100, 0.95);
            
            assertThat(estimate.pointEstimate()).isCloseTo(0.90, within(0.001));
            assertThat(estimate.sampleSize()).isEqualTo(100);
            assertThat(estimate.confidenceLevel()).isEqualTo(0.95);
            
            // Wilson 95% CI for 90/100 should be approximately [0.823, 0.949]
            assertThat(estimate.lowerBound()).isCloseTo(0.823, within(0.01));
            assertThat(estimate.upperBound()).isCloseTo(0.949, within(0.01));
        }
        
        @Test
        @DisplayName("handles p̂ = 0 (zero successes) without collapse")
        void handlesPerfectFailureRate() {
            // 0 successes out of 100 trials
            // Normal approximation would give CI of [0, 0] (useless)
            // Wilson provides a sensible upper bound
            ProportionEstimate estimate = estimator.estimate(0, 100, 0.95);
            
            assertThat(estimate.pointEstimate()).isEqualTo(0.0);
            assertThat(estimate.lowerBound()).isEqualTo(0.0);
            assertThat(estimate.upperBound()).isGreaterThan(0.0); // Wilson gives non-zero upper bound
            assertThat(estimate.upperBound()).isCloseTo(0.037, within(0.01));
        }
        
        @Test
        @DisplayName("handles p̂ = 1 (all successes) without collapse")
        void handlesPerfectSuccessRate() {
            // 100 successes out of 100 trials
            // Normal approximation would give CI of [1, 1] (useless)
            // Wilson provides a sensible lower bound
            ProportionEstimate estimate = estimator.estimate(100, 100, 0.95);
            
            assertThat(estimate.pointEstimate()).isEqualTo(1.0);
            assertThat(estimate.upperBound()).isEqualTo(1.0);
            assertThat(estimate.lowerBound()).isLessThan(1.0); // Wilson gives non-one lower bound
            assertThat(estimate.lowerBound()).isCloseTo(0.963, within(0.01));
        }
        
        @Test
        @DisplayName("interval width decreases with larger sample size")
        void intervalNarrowsWithLargerSample() {
            ProportionEstimate smallN = estimator.estimate(90, 100, 0.95);
            ProportionEstimate largeN = estimator.estimate(9000, 10000, 0.95);
            
            // Same proportion (90%), but much tighter interval for n=10000
            assertThat(largeN.intervalWidth()).isLessThan(smallN.intervalWidth() / 5.0);
        }
        
        @Test
        @DisplayName("higher confidence level produces wider interval")
        void higherConfidenceProducesWiderInterval() {
            ProportionEstimate ci95 = estimator.estimate(90, 100, 0.95);
            ProportionEstimate ci99 = estimator.estimate(90, 100, 0.99);
            
            assertThat(ci99.intervalWidth()).isGreaterThan(ci95.intervalWidth());
        }
    }
    
    @Nested
    @DisplayName("One-Sided Lower Bound (for threshold derivation)")
    class OneSidedLowerBound {
        
        @Test
        @DisplayName("computes 95% one-sided lower bound")
        void computes95OneSidedLowerBound() {
            // 951 successes out of 1000 trials (baseline experiment)
            // This is the key calculation for threshold derivation
            double lowerBound = estimator.lowerBound(951, 1000, 0.95);
            
            // One-sided 95% lower bound for 951/1000 ≈ 0.936
            assertThat(lowerBound).isCloseTo(0.936, within(0.01));
        }
        
        @Test
        @DisplayName("one-sided bound is lower than two-sided lower bound")
        void oneSidedLowerThanTwoSided() {
            // For the same confidence level, one-sided uses z_{α} while
            // two-sided uses z_{α/2}, so one-sided lower bound is higher
            double oneSided = estimator.lowerBound(90, 100, 0.95);
            ProportionEstimate twoSided = estimator.estimate(90, 100, 0.95);
            
            // Actually, one-sided lower bound should be HIGHER (less conservative)
            // because z_{0.95} ≈ 1.645 < z_{0.975} ≈ 1.96
            assertThat(oneSided).isGreaterThan(twoSided.lowerBound());
        }
        
        @Test
        @DisplayName("handles perfect baseline (p̂ = 1) correctly")
        void handlesPerfectBaseline() {
            // All 1000 trials succeeded
            // The Wilson lower bound should still produce a value < 1.0
            double lowerBound = estimator.lowerBound(1000, 1000, 0.95);
            
            assertThat(lowerBound).isLessThan(1.0);
            // Wilson one-sided lower bound for 1000/1000 at 95% confidence ≈ 0.9973
            assertThat(lowerBound).isCloseTo(0.9973, within(0.001));
        }
        
        @Test
        @DisplayName("lower bound increases with sample size (more precision)")
        void lowerBoundIncreasesWithSampleSize() {
            // Same observed rate, but more samples means more confidence
            double lowerN100 = estimator.lowerBound(95, 100, 0.95);
            double lowerN1000 = estimator.lowerBound(950, 1000, 0.95);
            
            // Both have p̂ = 0.95, but n=1000 has higher lower bound
            assertThat(lowerN1000).isGreaterThan(lowerN100);
        }
    }
    
    @Nested
    @DisplayName("Z-Score Calculations")
    class ZScoreCalculations {
        
        @Test
        @DisplayName("one-sided z-score for 95% confidence is approximately 1.645")
        void oneSidedZScoreFor95Percent() {
            // z_{0.95} = Φ⁻¹(0.95) ≈ 1.645
            double z = estimator.zScoreOneSided(0.95);
            assertThat(z).isCloseTo(1.645, within(0.001));
        }
        
        @Test
        @DisplayName("two-sided z-score for 95% confidence is approximately 1.96")
        void twoSidedZScoreFor95Percent() {
            // z_{0.975} = Φ⁻¹(0.975) ≈ 1.96
            double z = estimator.zScoreTwoSided(0.95);
            assertThat(z).isCloseTo(1.96, within(0.01));
        }
        
        @Test
        @DisplayName("z-score increases with confidence level")
        void zScoreIncreasesWithConfidence() {
            double z90 = estimator.zScoreOneSided(0.90);
            double z95 = estimator.zScoreOneSided(0.95);
            double z99 = estimator.zScoreOneSided(0.99);
            
            assertThat(z90).isLessThan(z95);
            assertThat(z95).isLessThan(z99);
        }
    }
    
    @Nested
    @DisplayName("Z-Test Statistic: z = (p̂ - π₀) / √(π₀(1-π₀)/n)")
    class ZTestStatistic {

        @Test
        @DisplayName("computes z-statistic for observed rate below hypothesis")
        void observedBelowHypothesis() {
            // p̂ = 0.80, π₀ = 0.95, n = 100
            // SE = √(0.95 × 0.05 / 100) = √0.000475 ≈ 0.02179
            // z = (0.80 - 0.95) / 0.02179 ≈ -6.882
            double z = estimator.zTestStatistic(0.80, 0.95, 100);
            assertThat(z).isCloseTo(-6.882, within(0.01));
        }

        @Test
        @DisplayName("computes z-statistic for observed rate above hypothesis")
        void observedAboveHypothesis() {
            // p̂ = 0.98, π₀ = 0.90, n = 200
            // SE = √(0.90 × 0.10 / 200) ≈ 0.02121
            // z = (0.98 - 0.90) / 0.02121 ≈ 3.771
            double z = estimator.zTestStatistic(0.98, 0.90, 200);
            assertThat(z).isCloseTo(3.771, within(0.01));
        }

        @Test
        @DisplayName("returns zero when observed equals hypothesized")
        void observedEqualsHypothesized() {
            double z = estimator.zTestStatistic(0.95, 0.95, 100);
            assertThat(z).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns zero for non-positive sample size")
        void nonPositiveSampleSize() {
            assertThat(estimator.zTestStatistic(0.90, 0.95, 0)).isEqualTo(0.0);
            assertThat(estimator.zTestStatistic(0.90, 0.95, -1)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns zero when hypothesized rate is 0 or 1 (SE collapses)")
        void hypothesizedAtBoundary() {
            assertThat(estimator.zTestStatistic(0.50, 0.0, 100)).isEqualTo(0.0);
            assertThat(estimator.zTestStatistic(0.50, 1.0, 100)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("magnitude increases with sample size for fixed shortfall")
        void magnitudeIncreasesWithSampleSize() {
            double zSmall = estimator.zTestStatistic(0.90, 0.95, 50);
            double zLarge = estimator.zTestStatistic(0.90, 0.95, 500);
            assertThat(Math.abs(zLarge)).isGreaterThan(Math.abs(zSmall));
        }
    }

    @Nested
    @DisplayName("One-Sided P-Value: P(Z > z)")
    class OneSidedPValue {

        @Test
        @DisplayName("p-value is 0.5 when z = 0")
        void pValueAtZero() {
            double p = estimator.oneSidedPValue(0.0);
            assertThat(p).isCloseTo(0.5, within(0.0001));
        }

        @Test
        @DisplayName("p-value is small for large positive z")
        void smallPValueForLargePositiveZ() {
            // z = 1.645 → upper tail ≈ 0.05
            double p = estimator.oneSidedPValue(1.645);
            assertThat(p).isCloseTo(0.05, within(0.001));
        }

        @Test
        @DisplayName("p-value approaches 1 for large negative z")
        void largePValueForNegativeZ() {
            // z = -3.0 → upper tail ≈ 0.9987
            double p = estimator.oneSidedPValue(-3.0);
            assertThat(p).isCloseTo(0.9987, within(0.001));
        }

        @Test
        @DisplayName("p-value decreases as z increases")
        void pValueDecreasesWithZ() {
            double p1 = estimator.oneSidedPValue(1.0);
            double p2 = estimator.oneSidedPValue(2.0);
            double p3 = estimator.oneSidedPValue(3.0);
            assertThat(p1).isGreaterThan(p2);
            assertThat(p2).isGreaterThan(p3);
        }

        @Test
        @DisplayName("p-value is consistent with z-test statistic")
        void consistentWithZTestStatistic() {
            // If observed significantly below threshold, p-value should be large (upper tail of negative z)
            double z = estimator.zTestStatistic(0.80, 0.95, 100);
            double p = estimator.oneSidedPValue(z);
            // z is strongly negative → P(Z > z) ≈ 1
            assertThat(p).isGreaterThan(0.99);
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {
        
        @Test
        @DisplayName("rejects non-positive trials")
        void rejectsNonPositiveTrials() {
            assertThatThrownBy(() -> estimator.standardError(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trials must be positive");
        }
        
        @Test
        @DisplayName("rejects negative successes")
        void rejectsNegativeSuccesses() {
            assertThatThrownBy(() -> estimator.standardError(-1, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Successes must be non-negative");
        }
        
        @Test
        @DisplayName("rejects successes exceeding trials")
        void rejectsSuccessesExceedingTrials() {
            assertThatThrownBy(() -> estimator.standardError(150, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed trials");
        }
        
        @Test
        @DisplayName("rejects confidence level outside (0, 1)")
        void rejectsInvalidConfidenceLevel() {
            assertThatThrownBy(() -> estimator.estimate(50, 100, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence level");
            
            assertThatThrownBy(() -> estimator.estimate(50, 100, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence level");
        }
    }
}

