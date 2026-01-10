package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Tests for samples/samplesPerConfig mutual exclusivity validation.
 *
 * <p>Uses a simple validation function that mimics the logic in ExperimentExtension
 * to avoid complex test setup with actual JUnit extension machinery.
 */
@DisplayName("Sample Configuration Validation")
class SampleConfigurationValidationTest {

    @Nested
    @DisplayName("Valid Configurations")
    class ValidConfigurations {

        @Test
        @DisplayName("should accept only samples specified")
        void shouldAcceptOnlySamples() {
            assertThatCode(() -> validateSampleConfiguration(100, 0, "testMethod"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should accept only samplesPerConfig specified")
        void shouldAcceptOnlySamplesPerConfig() {
            assertThatCode(() -> validateSampleConfiguration(0, 5, "testMethod"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should accept neither specified (uses defaults)")
        void shouldAcceptNeitherSpecified() {
            assertThatCode(() -> validateSampleConfiguration(0, 0, "testMethod"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Invalid Configurations")
    class InvalidConfigurations {

        @Test
        @DisplayName("should reject both samples AND samplesPerConfig specified")
        void shouldRejectBothSpecified() {
            assertThatThrownBy(() -> validateSampleConfiguration(100, 5, "invalidMethod"))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("mutually exclusive")
                    .hasMessageContaining("samples")
                    .hasMessageContaining("samplesPerConfig")
                    .hasMessageContaining("invalidMethod");
        }

        @Test
        @DisplayName("error message should provide guidance on resolution")
        void errorMessageShouldProvideGuidance() {
            assertThatThrownBy(() -> validateSampleConfiguration(100, 5, "testMethod"))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("MEASURE")
                    .hasMessageContaining("EXPLORE")
                    .hasMessageContaining("Remove one");
        }
    }

    /**
     * Replicates the validation logic from ExperimentExtension for testing.
     *
     * @param samples          value of samples attribute
     * @param samplesPerConfig value of samplesPerConfig attribute
     * @param methodName       method name for error messages
     */
    private void validateSampleConfiguration(int samples, int samplesPerConfig, String methodName) {
        boolean hasSamples = samples > 0;
        boolean hasSamplesPerConfig = samplesPerConfig > 0;

        if (hasSamples && hasSamplesPerConfig) {
            throw new ExtensionConfigurationException(
                    "Experiment method '" + methodName + "' specifies both 'samples' and 'samplesPerConfig'. " +
                            "These attributes are mutually exclusive:\n" +
                            "  - Use 'samples' for MEASURE mode (total sample count)\n" +
                            "  - Use 'samplesPerConfig' for EXPLORE mode (samples per factor configuration)\n" +
                            "Remove one of these attributes to resolve the conflict.");
        }
    }
}

