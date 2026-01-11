package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.annotation.Annotation;
import java.time.Instant;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.javai.punit.statistics.OperationalApproach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StatisticsAwareConfiguration}.
 *
 * <p>These tests verify that the configuration resolver correctly integrates
 * with the statistics engine to derive thresholds and sample sizes.
 */
@DisplayName("StatisticsAwareConfiguration")
class StatisticsAwareConfigurationTest {

    private StatisticsAwareConfiguration resolver;
    private ExecutionSpecification testSpec;

    @BeforeEach
    void setUp() {
        resolver = StatisticsAwareConfiguration.createDefault();
        
        // Create a spec with baseline data: 951/1000 successes (95.1%)
        testSpec = ExecutionSpecification.builder()
                .specId("json.generation:v1")
                .useCaseId("json.generation")
                .version(1)
                .approvedAt(Instant.now())
                .approvedBy("test")
                .approvalNotes("Test spec")
                .baselineData(1000, 951)
                .build();
    }

    @Nested
    @DisplayName("Sample-Size-First Approach")
    class SampleSizeFirstTests {

        @Test
        @DisplayName("derives threshold from baseline at 95% confidence")
        void derivesThresholdAt95Confidence() {
            ProbabilisticTest annotation = createAnnotation(
                    "json.generation:v1", 100, Double.NaN, 0.95,
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result = resolver.resolve(annotation, testSpec, "testMethod");

            assertThat(result.isSpecDriven()).isTrue();
            assertThat(result.getApproach()).isEqualTo(OperationalApproach.SAMPLE_SIZE_FIRST);
            assertThat(result.samples()).isEqualTo(100);
            
            // Threshold should be lower than baseline rate (95.1%)
            assertThat(result.minPassRate()).isLessThan(0.951);
            // But not too low (should be around 91-93%)
            assertThat(result.minPassRate()).isBetween(0.90, 0.94);
        }

        @Test
        @DisplayName("higher confidence produces lower threshold (more tolerant)")
        void higherConfidenceProducesLowerThreshold() {
            ProbabilisticTest annotation95 = createAnnotation(
                    "json.generation:v1", 100, Double.NaN, 0.95,
                    Double.NaN, Double.NaN, Double.NaN
            );
            ProbabilisticTest annotation99 = createAnnotation(
                    "json.generation:v1", 100, Double.NaN, 0.99,
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result95 = resolver.resolve(annotation95, testSpec, "test");
            var result99 = resolver.resolve(annotation99, testSpec, "test");

            // 99% confidence should have a lower threshold than 95%
            assertThat(result99.minPassRate()).isLessThan(result95.minPassRate());
        }
    }

    @Nested
    @DisplayName("Confidence-First Approach")
    class ConfidenceFirstTests {

        @Test
        @DisplayName("computes required sample size via power analysis")
        void computesRequiredSampleSize() {
            ProbabilisticTest annotation = createAnnotation(
                    "json.generation:v1", 100, Double.NaN, Double.NaN,
                    0.95, 0.05, 0.80  // 95% confidence, 5% effect, 80% power
            );

            var result = resolver.resolve(annotation, testSpec, "testMethod");

            assertThat(result.isSpecDriven()).isTrue();
            assertThat(result.getApproach()).isEqualTo(OperationalApproach.CONFIDENCE_FIRST);
            
            // Sample size should be computed (not the annotation default of 100)
            assertThat(result.samples()).isGreaterThan(0);
            
            // Threshold should be derived
            assertThat(result.minPassRate()).isLessThan(0.951);
        }

        @Test
        @DisplayName("larger effect size requires fewer samples")
        void largerEffectRequiresFewerSamples() {
            ProbabilisticTest annotation5pct = createAnnotation(
                    "json.generation:v1", 100, Double.NaN, Double.NaN,
                    0.95, 0.05, 0.80  // 5% effect
            );
            ProbabilisticTest annotation10pct = createAnnotation(
                    "json.generation:v1", 100, Double.NaN, Double.NaN,
                    0.95, 0.10, 0.80  // 10% effect
            );

            var result5pct = resolver.resolve(annotation5pct, testSpec, "test");
            var result10pct = resolver.resolve(annotation10pct, testSpec, "test");

            // Larger effect should require fewer samples
            assertThat(result10pct.samples()).isLessThan(result5pct.samples());
        }
    }

    @Nested
    @DisplayName("Threshold-First Approach")
    class ThresholdFirstTests {

        @Test
        @DisplayName("uses explicit threshold")
        void usesExplicitThreshold() {
            ProbabilisticTest annotation = createAnnotation(
                    "json.generation:v1", 100, 0.90, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result = resolver.resolve(annotation, testSpec, "testMethod");

            assertThat(result.isSpecDriven()).isTrue();
            assertThat(result.getApproach()).isEqualTo(OperationalApproach.THRESHOLD_FIRST);
            assertThat(result.samples()).isEqualTo(100);
            assertThat(result.minPassRate()).isEqualTo(0.90);
        }

        @Test
        @DisplayName("flags threshold equal to baseline rate as unsound")
        void flagsUnsoundThreshold() {
            // Using exact baseline rate as threshold (statistically unsound)
            ProbabilisticTest annotation = createAnnotation(
                    "json.generation:v1", 100, 0.951, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result = resolver.resolve(annotation, testSpec, "testMethod");

            assertThat(result.derivedThreshold().isStatisticallySound()).isFalse();
        }
    }

    @Nested
    @DisplayName("Spec-less Mode")
    class SpeclessModeTests {

        @Test
        @DisplayName("uses explicit threshold when no spec")
        void usesExplicitThresholdWithoutSpec() {
            ProbabilisticTest annotation = createAnnotation(
                    "", 50, 0.85, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result = resolver.resolve(annotation, null, "testMethod");

            assertThat(result.isSpecDriven()).isFalse();
            assertThat(result.samples()).isEqualTo(50);
            assertThat(result.minPassRate()).isEqualTo(0.85);
            assertThat(result.derivedThreshold()).isNull();
        }

        @Test
        @DisplayName("rejects when no approach specified without spec")
        void rejectsNoApproachWithoutSpec() {
            ProbabilisticTest annotation = createAnnotation(
                    "", 100, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, null, "testMethod"))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("requires you to specify an operational approach");
        }
    }

    @Nested
    @DisplayName("Perfect Baseline Handling")
    class PerfectBaselineTests {

        @Test
        @DisplayName("handles 100% baseline rate correctly")
        void handlesPerfectBaseline() {
            // Spec with perfect baseline: 1000/1000 (100%)
            ExecutionSpecification perfectSpec = ExecutionSpecification.builder()
                    .specId("perfect:v1")
                    .useCaseId("perfect")
                    .version(1)
                    .approvedAt(Instant.now())
                    .approvedBy("test")
                    .approvalNotes("Perfect baseline test")
                    .baselineData(1000, 1000)  // 100% success
                    .build();

            ProbabilisticTest annotation = createAnnotation(
                    "perfect:v1", 100, Double.NaN, 0.95,
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result = resolver.resolve(annotation, perfectSpec, "testMethod");

            // Threshold should be less than 1.0 (allowing for some failures)
            assertThat(result.minPassRate()).isLessThan(1.0);
            // But still very high (around 96-99%)
            assertThat(result.minPassRate()).isGreaterThan(0.95);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("rejects spec without baseline data")
        void rejectsSpecWithoutBaselineData() {
            ExecutionSpecification specWithoutBaseline = ExecutionSpecification.builder()
                    .specId("no-baseline:v1")
                    .useCaseId("no-baseline")
                    .version(1)
                    .approvedAt(Instant.now())
                    .approvedBy("test")
                    .approvalNotes("No baseline")
                    // No baselineData set
                    .build();

            ProbabilisticTest annotation = createAnnotation(
                    "no-baseline:v1", 100, Double.NaN, 0.95,
                    Double.NaN, Double.NaN, Double.NaN
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, specWithoutBaseline, "test"))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Missing Baseline Data");
        }
    }

    // ==================== Test Fixtures ====================

    private ProbabilisticTest createAnnotation(
            String spec, int samples, double minPassRate, double thresholdConfidence,
            double confidence, double minDetectableEffect, double power) {

        return new ProbabilisticTest() {
            @Override public Class<? extends Annotation> annotationType() { return ProbabilisticTest.class; }
            @Override public String spec() { return spec; }
            @Override public Class<?> useCase() { return Void.class; }
            @Override public String useCaseId() { return ""; }
            @Override public int samples() { return samples; }
            @Override public double minPassRate() { return minPassRate; }
            @Override public double thresholdConfidence() { return thresholdConfidence; }
            @Override public double confidence() { return confidence; }
            @Override public double minDetectableEffect() { return minDetectableEffect; }
            @Override public double power() { return power; }
            @Override public long timeBudgetMs() { return 0; }
            @Override public int tokenCharge() { return 0; }
            @Override public long tokenBudget() { return 0; }
            @Override public BudgetExhaustedBehavior onBudgetExhausted() { return BudgetExhaustedBehavior.FAIL; }
            @Override public ExceptionHandling onException() { return ExceptionHandling.FAIL_SAMPLE; }
            @Override public int maxExampleFailures() { return 5; }
            @Override public boolean transparentStats() { return false; }
        };
    }
}

