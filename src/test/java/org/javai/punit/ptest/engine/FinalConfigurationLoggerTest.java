package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.ptest.engine.FinalConfigurationLogger.ConfigurationData;
import org.javai.punit.reporting.PUnitReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FinalConfigurationLogger}.
 */
class FinalConfigurationLoggerTest {

    private FinalConfigurationLogger logger;
    private PUnitReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new PUnitReporter();
        logger = new FinalConfigurationLogger(reporter);
    }

    @Nested
    @DisplayName("Mode detection")
    class ModeDetection {

        @Test
        @DisplayName("SLA threshold shows SLA-DRIVEN mode")
        void slaThresholdShowsSlaMode() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.95,
                    "ShoppingUseCase",
                    ThresholdOrigin.SLA,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("Mode:");
            assertThat(formatted).contains("SLA-DRIVEN");
        }

        @Test
        @DisplayName("SLO threshold shows SLO-DRIVEN mode")
        void sloThresholdShowsSloMode() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.99,
                    "ShoppingUseCase",
                    ThresholdOrigin.SLO,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("SLO-DRIVEN");
        }

        @Test
        @DisplayName("POLICY threshold shows POLICY-DRIVEN mode")
        void policyThresholdShowsPolicyMode() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.90,
                    null,
                    ThresholdOrigin.POLICY,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("POLICY-DRIVEN");
        }

        @Test
        @DisplayName("Spec without normative origin shows SPEC-DRIVEN mode")
        void specWithoutNormativeOriginShowsSpecMode() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.92,
                    "ShoppingUseCase",
                    ThresholdOrigin.EMPIRICAL,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("Mode:");
            assertThat(formatted).contains("SPEC-DRIVEN");
            assertThat(formatted).contains("derived from baseline");
        }

        @Test
        @DisplayName("No spec and no normative origin shows EXPLICIT THRESHOLD mode")
        void noSpecShowsExplicitThresholdMode() {
            ConfigurationData config = new ConfigurationData(
                    50,
                    0.80,
                    null,
                    ThresholdOrigin.UNSPECIFIED,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("EXPLICIT THRESHOLD");
        }

        @Test
        @DisplayName("Null threshold origin shows EXPLICIT THRESHOLD mode")
        void nullThresholdOriginShowsExplicitMode() {
            ConfigurationData config = new ConfigurationData(
                    50,
                    0.80,
                    null,
                    null,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("EXPLICIT THRESHOLD");
        }
    }

    @Nested
    @DisplayName("Threshold formatting")
    class ThresholdFormatting {

        @Test
        @DisplayName("SLA threshold includes origin in parentheses")
        void slaThresholdIncludesOrigin() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.95,
                    "ShoppingUseCase",
                    ThresholdOrigin.SLA,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("Threshold:");
            assertThat(formatted).contains("95.0% (SLA)");
        }

        @Test
        @DisplayName("Spec-derived threshold shows baseline attribution")
        void specDerivedThresholdShowsBaselineAttribution() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.923,
                    "ShoppingUseCase",
                    null,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("92.3% (derived from baseline)");
        }

        @Test
        @DisplayName("Explicit threshold with EMPIRICAL origin shows origin note")
        void explicitThresholdWithEmpiricalOriginShowsNote() {
            ConfigurationData config = new ConfigurationData(
                    50,
                    0.85,
                    null,
                    ThresholdOrigin.EMPIRICAL,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("85.0% (EMPIRICAL)");
        }

        @Test
        @DisplayName("Explicit threshold with UNSPECIFIED origin has no note")
        void explicitThresholdWithUnspecifiedOriginHasNoNote() {
            ConfigurationData config = new ConfigurationData(
                    50,
                    0.80,
                    null,
                    ThresholdOrigin.UNSPECIFIED,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("80.0%");
            assertThat(formatted).doesNotContain("(UNSPECIFIED)");
        }
    }

    @Nested
    @DisplayName("Contract reference")
    class ContractReference {

        @Test
        @DisplayName("Contract reference appears when specified")
        void contractRefAppearsWhenSpecified() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.95,
                    "ShoppingUseCase",
                    ThresholdOrigin.SLA,
                    "SLA-2024-SHOP-001"
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("Contract:");
            assertThat(formatted).contains("SLA-2024-SHOP-001");
        }

        @Test
        @DisplayName("Contract reference does not appear when null")
        void contractRefDoesNotAppearWhenNull() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.95,
                    "ShoppingUseCase",
                    ThresholdOrigin.SLA,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).doesNotContain("Contract:");
        }

        @Test
        @DisplayName("Contract reference does not appear when empty")
        void contractRefDoesNotAppearWhenEmpty() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.95,
                    "ShoppingUseCase",
                    ThresholdOrigin.SLA,
                    ""
            );

            String formatted = logger.format(config);

            assertThat(formatted).doesNotContain("Contract:");
        }

        @Test
        @DisplayName("Contract reference only appears for normative thresholds")
        void contractRefOnlyAppearsForNormativeThresholds() {
            // Contract ref with EMPIRICAL origin (spec-driven mode)
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.92,
                    "ShoppingUseCase",
                    ThresholdOrigin.EMPIRICAL,
                    "SLA-2024-SHOP-001"
            );

            String formatted = logger.format(config);

            // In SPEC-DRIVEN mode, contract ref is not shown
            // (it would be confusing since threshold is from baseline, not contract)
            assertThat(formatted).contains("SPEC-DRIVEN");
            assertThat(formatted).doesNotContain("Contract:");
        }
    }

    @Nested
    @DisplayName("Use case / Spec ID")
    class UseCaseSpecId {

        @Test
        @DisplayName("Use Case appears for normative threshold with spec")
        void useCaseAppearsForNormativeWithSpec() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.95,
                    "ShoppingUseCase",
                    ThresholdOrigin.SLA,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("Use Case:");
            assertThat(formatted).contains("ShoppingUseCase");
        }

        @Test
        @DisplayName("Spec appears for spec-driven mode")
        void specAppearsForSpecDrivenMode() {
            ConfigurationData config = new ConfigurationData(
                    100,
                    0.92,
                    "ShoppingUseCase",
                    null,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("Spec:");
            assertThat(formatted).contains("ShoppingUseCase");
        }

        @Test
        @DisplayName("No Use Case or Spec for explicit threshold without spec")
        void noSpecForExplicitThreshold() {
            ConfigurationData config = new ConfigurationData(
                    50,
                    0.80,
                    null,
                    null,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).doesNotContain("Use Case:");
            assertThat(formatted).doesNotContain("Spec:");
        }
    }

    @Nested
    @DisplayName("Samples")
    class Samples {

        @Test
        @DisplayName("Sample count always appears")
        void sampleCountAlwaysAppears() {
            ConfigurationData config = new ConfigurationData(
                    150,
                    0.90,
                    null,
                    null,
                    null
            );

            String formatted = logger.format(config);

            assertThat(formatted).contains("Samples:");
            assertThat(formatted).contains("150");
        }
    }

    @Nested
    @DisplayName("ConfigurationData helper methods")
    class ConfigurationDataHelpers {

        @Test
        @DisplayName("isNormativeThreshold returns true for SLA")
        void isNormativeForSla() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, ThresholdOrigin.SLA, null);
            assertThat(config.isNormativeThreshold()).isTrue();
        }

        @Test
        @DisplayName("isNormativeThreshold returns true for SLO")
        void isNormativeForSlo() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, ThresholdOrigin.SLO, null);
            assertThat(config.isNormativeThreshold()).isTrue();
        }

        @Test
        @DisplayName("isNormativeThreshold returns true for POLICY")
        void isNormativeForPolicy() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, ThresholdOrigin.POLICY, null);
            assertThat(config.isNormativeThreshold()).isTrue();
        }

        @Test
        @DisplayName("isNormativeThreshold returns false for EMPIRICAL")
        void isNotNormativeForEmpirical() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, ThresholdOrigin.EMPIRICAL, null);
            assertThat(config.isNormativeThreshold()).isFalse();
        }

        @Test
        @DisplayName("isNormativeThreshold returns false for UNSPECIFIED")
        void isNotNormativeForUnspecified() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, ThresholdOrigin.UNSPECIFIED, null);
            assertThat(config.isNormativeThreshold()).isFalse();
        }

        @Test
        @DisplayName("isNormativeThreshold returns false for null")
        void isNotNormativeForNull() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, null, null);
            assertThat(config.isNormativeThreshold()).isFalse();
        }

        @Test
        @DisplayName("hasSpecId returns true when specId is present")
        void hasSpecIdWhenPresent() {
            ConfigurationData config = new ConfigurationData(100, 0.95, "MySpec", null, null);
            assertThat(config.hasSpecId()).isTrue();
        }

        @Test
        @DisplayName("hasSpecId returns false when specId is null")
        void hasSpecIdFalseWhenNull() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, null, null);
            assertThat(config.hasSpecId()).isFalse();
        }

        @Test
        @DisplayName("hasContractRef returns true when contractRef is present")
        void hasContractRefWhenPresent() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, null, "CONTRACT-123");
            assertThat(config.hasContractRef()).isTrue();
        }

        @Test
        @DisplayName("hasContractRef returns false when contractRef is null")
        void hasContractRefFalseWhenNull() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, null, null);
            assertThat(config.hasContractRef()).isFalse();
        }

        @Test
        @DisplayName("hasContractRef returns false when contractRef is empty")
        void hasContractRefFalseWhenEmpty() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, null, "");
            assertThat(config.hasContractRef()).isFalse();
        }

        @Test
        @DisplayName("hasThresholdOrigin returns true for non-UNSPECIFIED origin")
        void hasThresholdOriginWhenNotUnspecified() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, ThresholdOrigin.EMPIRICAL, null);
            assertThat(config.hasThresholdOrigin()).isTrue();
        }

        @Test
        @DisplayName("hasThresholdOrigin returns false for UNSPECIFIED")
        void hasThresholdOriginFalseForUnspecified() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, ThresholdOrigin.UNSPECIFIED, null);
            assertThat(config.hasThresholdOrigin()).isFalse();
        }

        @Test
        @DisplayName("hasThresholdOrigin returns false for null")
        void hasThresholdOriginFalseForNull() {
            ConfigurationData config = new ConfigurationData(100, 0.95, null, null, null);
            assertThat(config.hasThresholdOrigin()).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Null config returns empty string")
        void nullConfigReturnsEmptyString() {
            String formatted = logger.format(null);
            assertThat(formatted).isEmpty();
        }

        @Test
        @DisplayName("log method handles null config gracefully")
        void logHandlesNullConfig() {
            // Should not throw
            logger.log("testMethod", null);
        }
    }
}

