package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.annotation.Annotation;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.statistics.OperationalApproach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OperationalApproachResolver}.
 *
 * <p>These tests verify that the resolver correctly identifies operational approaches
 * and provides developer-friendly error messages for misconfiguration.
 */
@DisplayName("OperationalApproachResolver")
class OperationalApproachResolverTest {

    private OperationalApproachResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OperationalApproachResolver();
    }

    @Nested
    @DisplayName("Sample-Size-First Approach")
    class SampleSizeFirstTests {

        @Test
        @DisplayName("detects Sample-Size-First when thresholdConfidence is set")
        void detectsSampleSizeFirst() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, Double.NaN, 0.95, // thresholdConfidence set
                    Double.NaN, Double.NaN, Double.NaN   // confidence-first params NOT set
            );

            var result = resolver.resolve(annotation, true);

            assertThat(result.approach()).isEqualTo(OperationalApproach.SAMPLE_SIZE_FIRST);
            assertThat(result.samples()).isEqualTo(100);
            assertThat(result.confidence()).isEqualTo(0.95);
            assertThat(result.isSpecDriven()).isTrue();
        }
    }

    @Nested
    @DisplayName("Confidence-First Approach")
    class ConfidenceFirstTests {

        @Test
        @DisplayName("detects Confidence-First when all three parameters are set")
        void detectsConfidenceFirst() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, Double.NaN, Double.NaN,
                    0.99, 0.05, 0.80  // confidence-first params all set
            );

            var result = resolver.resolve(annotation, true);

            assertThat(result.approach()).isEqualTo(OperationalApproach.CONFIDENCE_FIRST);
            assertThat(result.confidence()).isEqualTo(0.99);
            assertThat(result.minDetectableEffect()).isEqualTo(0.05);
            assertThat(result.power()).isEqualTo(0.80);
            assertThat(result.samples()).isEqualTo(-1); // Will be computed
            assertThat(result.isSpecDriven()).isTrue();
        }

        @Test
        @DisplayName("rejects partial Confidence-First (missing minDetectableEffect)")
        void rejectsPartialConfidenceFirst_missingEffect() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, Double.NaN, Double.NaN,
                    0.99, Double.NaN, 0.80  // minDetectableEffect missing
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, true))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Incomplete Confidence-First")
                    .hasMessageContaining("minDetectableEffect");
        }

        @Test
        @DisplayName("rejects partial Confidence-First (missing power)")
        void rejectsPartialConfidenceFirst_missingPower() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, Double.NaN, Double.NaN,
                    0.99, 0.05, Double.NaN  // power missing
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, true))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Incomplete Confidence-First")
                    .hasMessageContaining("power");
        }

        @Test
        @DisplayName("rejects partial Confidence-First (missing confidence)")
        void rejectsPartialConfidenceFirst_missingConfidence() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, Double.NaN, Double.NaN,
                    Double.NaN, 0.05, 0.80  // confidence missing
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, true))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Incomplete Confidence-First")
                    .hasMessageContaining("confidence");
        }
    }

    @Nested
    @DisplayName("Threshold-First Approach")
    class ThresholdFirstTests {

        @Test
        @DisplayName("detects Threshold-First when minPassRate is set")
        void detectsThresholdFirst() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, 0.95, Double.NaN,  // minPassRate set
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result = resolver.resolve(annotation, true);

            assertThat(result.approach()).isEqualTo(OperationalApproach.THRESHOLD_FIRST);
            assertThat(result.samples()).isEqualTo(100);
            assertThat(result.minPassRate()).isEqualTo(0.95);
            assertThat(result.isSpecDriven()).isTrue();
        }
    }

    @Nested
    @DisplayName("Conflict Detection")
    class ConflictDetectionTests {

        @Test
        @DisplayName("rejects when both Sample-Size-First and Threshold-First parameters are set")
        void rejectsConflict_sampleSizeFirstAndThresholdFirst() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, 0.95, 0.95,  // Both minPassRate AND thresholdConfidence
                    Double.NaN, Double.NaN, Double.NaN
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, true))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Conflicting Approaches")
                    .hasMessageContaining("Sample-Size-First")
                    .hasMessageContaining("Threshold-First");
        }

        @Test
        @DisplayName("rejects when all three approaches have parameters set")
        void rejectsConflict_allThreeApproaches() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, 0.95, 0.95,  // minPassRate + thresholdConfidence
                    0.99, 0.05, 0.80               // confidence-first params
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, true))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Conflicting Approaches");
        }
    }

    @Nested
    @DisplayName("Spec-less Mode")
    class SpeclessModeTests {

        @Test
        @DisplayName("rejects when no spec and no approach parameters")
        void rejectsNoApproachWithoutSpec() {
            ProbabilisticTest annotation = createAnnotation(
                    "", 100, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, false))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("requires you to specify an operational approach")
                    .hasMessageContaining("Threshold-First");
        }

        @Test
        @DisplayName("accepts Threshold-First in spec-less mode")
        void acceptsThresholdFirstWithoutSpec() {
            ProbabilisticTest annotation = createAnnotation(
                    "", 100, 0.90, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN
            );

            var result = resolver.resolve(annotation, false);

            assertThat(result.isSpecless()).isTrue();
            assertThat(result.approach()).isEqualTo(OperationalApproach.THRESHOLD_FIRST);
            assertThat(result.samples()).isEqualTo(100);
            assertThat(result.minPassRate()).isEqualTo(0.90);
            assertThat(result.isSpecDriven()).isFalse();
        }

        @Test
        @DisplayName("rejects Sample-Size-First without spec")
        void rejectsSampleSizeFirstWithoutSpec() {
            ProbabilisticTest annotation = createAnnotation(
                    "", 100, Double.NaN, 0.95,  // thresholdConfidence set but no spec
                    Double.NaN, Double.NaN, Double.NaN
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, false))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Sample-Size-First")
                    .hasMessageContaining("requires a spec");
        }

        @Test
        @DisplayName("rejects Confidence-First without spec")
        void rejectsConfidenceFirstWithoutSpec() {
            ProbabilisticTest annotation = createAnnotation(
                    "", 100, Double.NaN, Double.NaN,
                    0.99, 0.05, 0.80  // confidence-first set but no spec
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, false))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("Confidence-First")
                    .hasMessageContaining("requires a spec");
        }
    }

    @Nested
    @DisplayName("No Approach Specified with Spec")
    class NoApproachWithSpecTests {

        @Test
        @DisplayName("rejects when spec provided but no approach specified")
        void rejectsNoApproachWithSpec() {
            ProbabilisticTest annotation = createAnnotation(
                    "my-spec:v1", 100, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN  // No approach params
            );

            assertThatThrownBy(() -> resolver.resolve(annotation, true))
                    .isInstanceOf(ProbabilisticTestConfigurationException.class)
                    .hasMessageContaining("no operational approach was specified")
                    .hasMessageContaining("Sample-Size-First")
                    .hasMessageContaining("Confidence-First")
                    .hasMessageContaining("Threshold-First");
        }
    }

    // ==================== Test Fixtures ====================

    /**
     * Creates a mock annotation with the specified parameters.
     */
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
            @Override public org.javai.punit.api.BudgetExhaustedBehavior onBudgetExhausted() {
                return org.javai.punit.api.BudgetExhaustedBehavior.FAIL;
            }
            @Override public org.javai.punit.api.ExceptionHandling onException() {
                return org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE;
            }
            @Override public int maxExampleFailures() { return 5; }
            @Override public boolean transparentStats() { return false; }
        };
    }
}

