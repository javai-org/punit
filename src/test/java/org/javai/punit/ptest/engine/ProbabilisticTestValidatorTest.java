package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.spec.model.ExecutionSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProbabilisticTestValidator}.
 *
 * <p>These tests verify the five validation rules:
 * <ol>
 *   <li>Baseline + explicit minPassRate = CONFLICT</li>
 *   <li>No baseline + no threshold = UNDEFINED</li>
 *   <li>thresholdConfidence requires baseline</li>
 *   <li>Confidence-First requires baseline</li>
 *   <li>Partial Confidence-First = INCOMPLETE</li>
 * </ol>
 */
@DisplayName("ProbabilisticTestValidator")
class ProbabilisticTestValidatorTest {

    private ProbabilisticTestValidator validator;
    private ExecutionSpecification mockBaseline;

    @BeforeEach
    void setUp() {
        validator = new ProbabilisticTestValidator();
        mockBaseline = createMockBaseline();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RULE 1: Baseline + explicit minPassRate = CONFLICT
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule 1: Baseline + explicit minPassRate")
    class Rule1_BaselineWithExplicitThreshold {

        @Test
        @DisplayName("should reject when baseline exists and minPassRate is specified")
        void rejectsBaselineWithExplicitMinPassRate() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    0.95,       // minPassRate - explicit!
                    Double.NaN, // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0)).contains("CONFLICT");
            assertThat(result.errors().get(0)).contains("Baseline exists but explicit minPassRate specified");
        }

        @Test
        @DisplayName("should accept when baseline exists and minPassRate is NOT specified")
        void acceptsBaselineWithoutExplicitMinPassRate() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate - not specified
                    0.95,       // thresholdConfidence - Sample-Size-First
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("should accept when baseline exists and minPassRate specified with SLA origin")
        void acceptsBaselineWithMinPassRateAndSlaOrigin() {
            // When thresholdOrigin is SLA/SLO/POLICY, the developer is intentionally
            // overriding the baseline threshold with a normative requirement
            ProbabilisticTest annotation = createAnnotationWithOrigin(
                    100,        // samples
                    0.95,       // minPassRate - explicit SLA threshold
                    Double.NaN, // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN, // power
                    ThresholdOrigin.SLA
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("should reject when baseline exists and minPassRate specified with EMPIRICAL origin")
        void rejectsBaselineWithMinPassRateAndEmpiricalOrigin() {
            // EMPIRICAL origin is contradictory when overriding baseline - the empirical
            // value should come FROM the baseline, not override it
            ProbabilisticTest annotation = createAnnotationWithOrigin(
                    100,        // samples
                    0.95,       // minPassRate - explicit
                    Double.NaN, // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN, // power
                    ThresholdOrigin.EMPIRICAL
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors().get(0)).contains("CONFLICT");
            assertThat(result.errors().get(0)).contains("EMPIRICAL is contradictory");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RULE 2: No baseline + no threshold = UNDEFINED
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule 2: No baseline + no threshold")
    class Rule2_NoBaselineNoThreshold {

        @Test
        @DisplayName("should reject when no baseline and no minPassRate")
        void rejectsNoBaselineNoThreshold() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate - not specified
                    Double.NaN, // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, null, "testMethod");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0)).contains("UNDEFINED");
            assertThat(result.errors().get(0)).contains("No threshold specified and no baseline available");
        }

        @Test
        @DisplayName("should accept when no baseline but explicit minPassRate")
        void acceptsNoBaselineWithExplicitMinPassRate() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    0.95,       // minPassRate - explicit threshold
                    Double.NaN, // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, null, "testMethod");

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RULE 3: thresholdConfidence requires baseline
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule 3: thresholdConfidence requires baseline")
    class Rule3_ThresholdConfidenceRequiresBaseline {

        @Test
        @DisplayName("should reject thresholdConfidence without baseline")
        void rejectsThresholdConfidenceWithoutBaseline() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate
                    0.95,       // thresholdConfidence - specified!
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, null, "testMethod");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors().stream().anyMatch(e -> 
                    e.contains("INVALID") && e.contains("thresholdConfidence requires baseline")))
                    .isTrue();
        }

        @Test
        @DisplayName("should accept thresholdConfidence with baseline")
        void acceptsThresholdConfidenceWithBaseline() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate
                    0.95,       // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RULE 4: Confidence-First requires baseline
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule 4: Confidence-First requires baseline")
    class Rule4_ConfidenceFirstRequiresBaseline {

        @Test
        @DisplayName("should reject Confidence-First without baseline and without explicit minPassRate")
        void rejectsConfidenceFirstWithoutBaselineOrMinPassRate() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples (ignored in Confidence-First)
                    Double.NaN, // minPassRate - NOT specified
                    Double.NaN, // thresholdConfidence
                    0.99,       // confidence
                    0.05,       // minDetectableEffect
                    0.80        // power
            );

            var result = validator.validate(annotation, null, "testMethod");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors().stream().anyMatch(e -> 
                    e.contains("INVALID") && e.contains("Confidence-First approach requires a baseline rate")))
                    .isTrue();
        }

        @Test
        @DisplayName("should accept Confidence-First with baseline")
        void acceptsConfidenceFirstWithBaseline() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate
                    Double.NaN, // thresholdConfidence
                    0.99,       // confidence
                    0.05,       // minDetectableEffect
                    0.80        // power
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("should accept Confidence-First with explicit minPassRate (no baseline)")
        void acceptsConfidenceFirstWithExplicitMinPassRate() {
            // This is the SLA-driven Confidence-First scenario:
            // - minPassRate comes from an SLA (not baseline)
            // - Confidence-First parameters compute sample size
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples (will be overridden by power analysis)
                    0.95,       // minPassRate - explicit SLA threshold
                    Double.NaN, // thresholdConfidence
                    0.95,       // confidence
                    0.02,       // minDetectableEffect
                    0.80        // power
            );

            // No baseline provided - minPassRate serves as the baseline rate
            var result = validator.validate(annotation, null, "testMethod");

            assertThat(result.valid()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RULE 5: Partial Confidence-First = INCOMPLETE
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule 5: Partial Confidence-First")
    class Rule5_PartialConfidenceFirst {

        @Test
        @DisplayName("should reject when only confidence is specified")
        void rejectsConfidenceOnly() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate
                    Double.NaN, // thresholdConfidence
                    0.99,       // confidence - only this one!
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors().stream().anyMatch(e -> 
                    e.contains("INCOMPLETE") && e.contains("Partial Confidence-First")))
                    .isTrue();
        }

        @Test
        @DisplayName("should reject when two of three Confidence-First params specified")
        void rejectsTwoOfThree() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate
                    Double.NaN, // thresholdConfidence
                    0.99,       // confidence
                    0.05,       // minDetectableEffect
                    Double.NaN  // power - missing!
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors().stream().anyMatch(e -> 
                    e.contains("INCOMPLETE") && e.contains("Missing: power")))
                    .isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Valid configurations
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Valid configurations")
    class ValidConfigurations {

        @Test
        @DisplayName("Threshold-First without baseline (spec-less mode)")
        void thresholdFirstSpecless() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    0.95,       // minPassRate - explicit
                    Double.NaN, // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, null, "testMethod");

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("Sample-Size-First with baseline")
        void sampleSizeFirstWithBaseline() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples
                    Double.NaN, // minPassRate - will be derived
                    0.95,       // thresholdConfidence
                    Double.NaN, // confidence
                    Double.NaN, // minDetectableEffect
                    Double.NaN  // power
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("Confidence-First with baseline")
        void confidenceFirstWithBaseline() {
            ProbabilisticTest annotation = createAnnotation(
                    100,        // samples - will be computed
                    Double.NaN, // minPassRate - will be derived
                    Double.NaN, // thresholdConfidence
                    0.99,       // confidence
                    0.05,       // minDetectableEffect
                    0.80        // power
            );

            var result = validator.validate(annotation, mockBaseline, "testMethod");

            assertThat(result.valid()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test helpers
    // ═══════════════════════════════════════════════════════════════════════

    private ExecutionSpecification createMockBaseline() {
        // Create a minimal mock baseline
        return ExecutionSpecification.builder()
                .useCaseId("TestUseCase")
                .requirements(0.95, "Test success criteria")
                .empiricalBasis(100, 95)
                .build();
    }

    private ProbabilisticTest createAnnotation(
            int samples,
            double minPassRate,
            double thresholdConfidence,
            double confidence,
            double minDetectableEffect,
            double power) {
        
        return new ProbabilisticTest() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ProbabilisticTest.class;
            }

            @Override
            public int samples() {
                return samples;
            }

            @Override
            public double minPassRate() {
                return minPassRate;
            }

            @Override
            public long timeBudgetMs() {
                return 0;
            }

            @Override
            public int tokenCharge() {
                return 0;
            }

            @Override
            public long tokenBudget() {
                return 0;
            }

            @Override
            public BudgetExhaustedBehavior onBudgetExhausted() {
                return BudgetExhaustedBehavior.FAIL;
            }

            @Override
            public ExceptionHandling onException() {
                return ExceptionHandling.FAIL_SAMPLE;
            }

            @Override
            public int maxExampleFailures() {
                return 5;
            }

            @Override
            public Class<?> useCase() {
                return Void.class;
            }

            @Override
            public double thresholdConfidence() {
                return thresholdConfidence;
            }

            @Override
            public double confidence() {
                return confidence;
            }

            @Override
            public double minDetectableEffect() {
                return minDetectableEffect;
            }

            @Override
            public double power() {
                return power;
            }

            @Override
            public boolean transparentStats() {
                return false;
            }

            @Override
            public ThresholdOrigin thresholdOrigin() {
                return ThresholdOrigin.UNSPECIFIED;
            }

            @Override
            public String contractRef() {
                return "";
            }
        };
    }

    private ProbabilisticTest createAnnotationWithOrigin(
            int samples,
            double minPassRate,
            double thresholdConfidence,
            double confidence,
            double minDetectableEffect,
            double power,
            ThresholdOrigin origin) {
        
        return new ProbabilisticTest() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ProbabilisticTest.class;
            }

            @Override
            public int samples() {
                return samples;
            }

            @Override
            public double minPassRate() {
                return minPassRate;
            }

            @Override
            public long timeBudgetMs() {
                return 0;
            }

            @Override
            public int tokenCharge() {
                return 0;
            }

            @Override
            public long tokenBudget() {
                return 0;
            }

            @Override
            public BudgetExhaustedBehavior onBudgetExhausted() {
                return BudgetExhaustedBehavior.FAIL;
            }

            @Override
            public ExceptionHandling onException() {
                return ExceptionHandling.FAIL_SAMPLE;
            }

            @Override
            public int maxExampleFailures() {
                return 5;
            }

            @Override
            public Class<?> useCase() {
                return Void.class;
            }

            @Override
            public double thresholdConfidence() {
                return thresholdConfidence;
            }

            @Override
            public double confidence() {
                return confidence;
            }

            @Override
            public double minDetectableEffect() {
                return minDetectableEffect;
            }

            @Override
            public double power() {
                return power;
            }

            @Override
            public boolean transparentStats() {
                return false;
            }

            @Override
            public ThresholdOrigin thresholdOrigin() {
                return origin;
            }

            @Override
            public String contractRef() {
                return "";
            }
        };
    }
}

