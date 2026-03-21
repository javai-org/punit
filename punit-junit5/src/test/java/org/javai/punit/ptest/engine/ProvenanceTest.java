package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.AlwaysPassingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceContractRefOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceEmpiricalSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenancePolicySourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSlaSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSloSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceThresholdOriginOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceUnspecifiedTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceWithBothTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Tests for SLA provenance feature in probabilistic tests.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Provenance information is included in verdict output when specified</li>
 *   <li>Provenance is omitted when not specified</li>
 *   <li>All ThresholdOrigin values are rendered correctly</li>
 * </ul>
 */
class ProvenanceTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @Test
    void noProvenanceSet_verdictDoesNotIncludeProvenance() {
        String output = captureTestOutput(AlwaysPassingTest.class);

        assertThat(output).doesNotContain("Threshold origin:");
        assertThat(output).doesNotContain("Contract:");
    }

    @Test
    void provenanceUnspecified_verdictDoesNotIncludeProvenance() {
        String output = captureTestOutput(ProvenanceUnspecifiedTest.class);

        assertThat(output).doesNotContain("Threshold origin:");
        assertThat(output).doesNotContain("Contract:");
    }

    @Test
    void thresholdOriginOnly_includedInVerdict() {
        String output = captureTestOutput(ProvenanceThresholdOriginOnlyTest.class);

        assertThat(output).contains("Threshold origin:").contains("SLO");
        assertThat(output).doesNotContain("Contract:");
    }

    @Test
    void contractRefOnly_includedInVerdict() {
        String output = captureTestOutput(ProvenanceContractRefOnlyTest.class);

        assertThat(output).contains("Contract:").contains("Internal Policy DOC-001");
        assertThat(output).doesNotContain("Threshold origin:");
    }

    @Test
    void bothSet_includedInCorrectOrder() {
        String output = captureTestOutput(ProvenanceWithBothTest.class);

        assertThat(output).contains("Threshold origin:").contains("SLA");
        assertThat(output).contains("Contract:").contains("Acme API SLA v3.2 §2.1");

        // Verify order within verdict: thresholdOrigin before contract
        // Use lastIndexOf to match the verdict section (config section may also contain "Contract:")
        int thresholdOriginIndex = output.lastIndexOf("Threshold origin:");
        int contractIndex = output.lastIndexOf("Contract:");
        assertThat(thresholdOriginIndex)
                .as("Threshold origin should appear before Contract in verdict")
                .isLessThan(contractIndex);
    }

    @Test
    void slaSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceSlaSourceTest.class);
        assertThat(output).contains("Threshold origin:").contains("SLA");
    }

    @Test
    void sloSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceSloSourceTest.class);
        assertThat(output).contains("Threshold origin:").contains("SLO");
    }

    @Test
    void policySource_renderedCorrectly() {
        String output = captureTestOutput(ProvenancePolicySourceTest.class);
        assertThat(output).contains("Threshold origin:").contains("POLICY");
    }

    @Test
    void empiricalSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceEmpiricalSourceTest.class);
        assertThat(output).contains("Threshold origin:").contains("EMPIRICAL");
    }

    @Test
    void configurationResolver_extractsProvenance() {
        ConfigurationResolver.ResolvedConfiguration config =
            new ConfigurationResolver.ResolvedConfiguration(
                100, 0.95, 1.0, 0, 0, 0,
                org.javai.punit.api.BudgetExhaustedBehavior.FAIL,
                org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE,
                5,
                null, null, null, null,
                ThresholdOrigin.SLA, "Test Contract",
                org.javai.punit.api.TestIntent.VERIFICATION, 0.95
            );

        assertThat(config.hasProvenance()).isTrue();
        assertThat(config.hasThresholdOrigin()).isTrue();
        assertThat(config.hasContractRef()).isTrue();
        assertThat(config.thresholdOrigin()).isEqualTo(ThresholdOrigin.SLA);
        assertThat(config.contractRef()).isEqualTo("Test Contract");
    }

    @Test
    void configurationResolver_noProvenance_hasProvenanceFalse() {
        ConfigurationResolver.ResolvedConfiguration config =
            new ConfigurationResolver.ResolvedConfiguration(
                100, 0.95, 1.0, 0, 0, 0,
                org.javai.punit.api.BudgetExhaustedBehavior.FAIL,
                org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE,
                5,
                null, null, null, null,
                ThresholdOrigin.UNSPECIFIED, "",
                org.javai.punit.api.TestIntent.VERIFICATION, 0.95
            );

        assertThat(config.hasProvenance()).isFalse();
        assertThat(config.hasThresholdOrigin()).isFalse();  // UNSPECIFIED
        assertThat(config.hasContractRef()).isFalse();  // empty string
    }

    /**
     * Captures stdout output from running a test class via TestKit.
     */
    private String captureTestOutput(Class<?> testClass) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        PrintStream capturePrint = new PrintStream(capture);
        System.setOut(capturePrint);
        System.setErr(capturePrint);

        try {
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                    .selectors(DiscoverySelectors.selectClass(testClass))
                    .execute();

            return capture.toString();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
