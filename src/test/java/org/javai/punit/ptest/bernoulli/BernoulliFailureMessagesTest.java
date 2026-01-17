package org.javai.punit.ptest.bernoulli;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BernoulliFailureMessages} to ensure failure messages contain
 * all required statistical qualifications.
 */
class BernoulliFailureMessagesTest {

    @Test
    @DisplayName("Probabilistic test failure message includes all statistical qualifications")
    void probabilisticTestFailure_includesAllStatisticalQualifications() {
        // Given
        BernoulliFailureMessages.StatisticalContext context = new BernoulliFailureMessages.StatisticalContext(
                0.95,      // confidence
                0.87,      // observedRate
                87,        // successes
                100,       // samples
                0.916,     // threshold
                0.951,     // baselineRate
                1000,      // baselineSamples
                "json.generation:v3"
        );

        // When
        String message = BernoulliFailureMessages.probabilisticTestFailure(context);

        // Then
        assertThat(message)
                .contains("PUnit FAILED")
                .contains("95.0% confidence")
                .contains("alpha=0.050")
                .contains("87.0%")
                .contains("(87/100)")
                .contains("min pass rate=91.6%")
                .contains("Baseline=95.1%")
                .contains("N=1000")
                .contains("spec=json.generation:v3");
    }

    @Test
    @DisplayName("Probabilistic test failure message format matches expected pattern")
    void probabilisticTestFailure_matchesExpectedFormat() {
        // Given
        BernoulliFailureMessages.StatisticalContext context = new BernoulliFailureMessages.StatisticalContext(
                0.95,
                0.87,
                87,
                100,
                0.916,
                0.951,
                1000,
                "json.generation:v3"
        );

        // When
        String message = BernoulliFailureMessages.probabilisticTestFailure(context);

        // Then - verify exact format
        assertThat(message).isEqualTo(
                "PUnit FAILED with 95.0% confidence (alpha=0.050). " +
                "Observed pass rate=87.0% (87/100) < min pass rate=91.6%. " +
                "Baseline=95.1% (N=1000), spec=json.generation:v3"
        );
    }

    @Test
    @DisplayName("Legacy failure message works without statistical context")
    void probabilisticTestFailureLegacy_worksWithoutStatisticalContext() {
        // When
        String message = BernoulliFailureMessages.probabilisticTestFailureLegacy(
                0.87,  // observedRate
                87,    // successes
                100,   // samples
                0.90   // threshold
        );

        // Then
        assertThat(message)
                .contains("PUnit FAILED")
                .contains("87.0%")
                .contains("(87/100)")
                .contains("min pass rate=90.0%")
                .doesNotContain("confidence")
                .doesNotContain("Baseline");
    }

    @Test
    @DisplayName("Latency regression failure message includes all required fields")
    void latencyRegressionFailure_includesAllRequiredFields() {
        // Given
        BernoulliFailureMessages.LatencyStatisticalContext context = new BernoulliFailureMessages.LatencyStatisticalContext(
                0.95,      // confidence
                0.121,     // observedExceedanceRate
                12,        // exceedances
                99,        // effectiveSamples
                0.08,      // maxAllowedExceedanceRate
                45.2,      // thresholdMs
                0.95,      // thresholdQuantile
                0.05,      // baselineExceedanceRate
                999,       // baselineEffectiveSamples
                "checkout.service:v1"
        );

        // When
        String message = BernoulliFailureMessages.latencyRegressionFailure(context);

        // Then
        assertThat(message)
                .contains("PUnit LATENCY FAILED")
                .contains("95.0% confidence")
                .contains("alpha=0.050")
                .contains("12.1%")
                .contains("(12/99)")
                .contains("max allowed=8.0%")
                .contains("45.20ms")
                .contains("baseline p95")
                .contains("baseline exceedance=5.0%")
                .contains("N=999")
                .contains("spec=checkout.service:v1");
    }

    @Test
    @DisplayName("Latency timeout failure message includes all required fields")
    void latencyTimeoutFailure_includesAllRequiredFields() {
        // When
        String message = BernoulliFailureMessages.latencyTimeoutFailure(
                5,      // timeoutCount
                5,      // maxAllowedTimeouts
                47,     // samplesAttempted
                100,    // samplesPlanned
                "checkout.service:v1"
        );

        // Then
        assertThat(message)
                .contains("PUnit LATENCY FAILED")
                .contains("timeout threshold exceeded")
                .contains("Timeouts=5")
                .contains("max allowed=5")
                .contains("47/100")
                .contains("spec=checkout.service:v1");
    }

    @Test
    @DisplayName("StatisticalContext forLegacyMode creates context with placeholder values")
    void statisticalContext_forLegacyMode_createsPlaceholderContext() {
        // When
        BernoulliFailureMessages.StatisticalContext context =
                BernoulliFailureMessages.StatisticalContext.forLegacyMode(0.87, 87, 100, 0.90);

        // Then
        assertThat(context.isSpecDriven()).isFalse();
        assertThat(context.observedRate()).isEqualTo(0.87);
        assertThat(context.successes()).isEqualTo(87);
        assertThat(context.samples()).isEqualTo(100);
        assertThat(context.threshold()).isEqualTo(0.90);
        assertThat(Double.isNaN(context.confidence())).isTrue();
        assertThat(Double.isNaN(context.baselineRate())).isTrue();
        assertThat(context.baselineSamples()).isZero();
        assertThat(context.specId()).isEqualTo("(inline)");
    }

    @Test
    @DisplayName("StatisticalContext isSpecDriven returns true for spec-driven context")
    void statisticalContext_isSpecDriven_returnsTrueForSpecDrivenContext() {
        // Given
        BernoulliFailureMessages.StatisticalContext context = new BernoulliFailureMessages.StatisticalContext(
                0.95, 0.87, 87, 100, 0.916, 0.951, 1000, "test:v1"
        );

        // Then
        assertThat(context.isSpecDriven()).isTrue();
    }

    @Test
    @DisplayName("High confidence level shows small alpha")
    void probabilisticTestFailure_highConfidence_showsSmallAlpha() {
        // Given - 99% confidence
        BernoulliFailureMessages.StatisticalContext context = new BernoulliFailureMessages.StatisticalContext(
                0.99, 0.87, 87, 100, 0.916, 0.951, 1000, "test:v1"
        );

        // When
        String message = BernoulliFailureMessages.probabilisticTestFailure(context);

        // Then
        assertThat(message)
                .contains("99.0% confidence")
                .contains("alpha=0.010");
    }

    @Test
    @DisplayName("Edge case: 100% observed rate still shows failure correctly")
    void probabilisticTestFailure_edgeCase_hundredPercentObserved() {
        // Given - observed 100% but threshold somehow not met (edge case)
        BernoulliFailureMessages.StatisticalContext context = new BernoulliFailureMessages.StatisticalContext(
                0.95, 1.0, 100, 100, 1.0, 1.0, 1000, "test:v1"
        );

        // When
        String message = BernoulliFailureMessages.probabilisticTestFailure(context);

        // Then
        assertThat(message)
                .contains("100.0%")
                .contains("(100/100)")
                .contains("min pass rate=100.0%");
    }
}

