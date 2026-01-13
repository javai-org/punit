package org.javai.punit.ptest.engine;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceContractRefOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceEmpiricalSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenancePolicySourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSlaSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSloSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceThresholdOriginOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceUnspecifiedTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceWithBothTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.AlwaysPassingTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(output).doesNotContain("Contract ref:");
    }

    @Test
    void provenanceUnspecified_verdictDoesNotIncludeProvenance() {
        String output = captureTestOutput(ProvenanceUnspecifiedTest.class);
        
        assertThat(output).doesNotContain("Threshold origin:");
        assertThat(output).doesNotContain("Contract ref:");
    }

    @Test
    void thresholdOriginOnly_includedInVerdict() {
        String output = captureTestOutput(ProvenanceThresholdOriginOnlyTest.class);
        
        assertThat(output).contains("Threshold origin: SLO");
        assertThat(output).doesNotContain("Contract ref:");
    }

    @Test
    void contractRefOnly_includedInVerdict() {
        String output = captureTestOutput(ProvenanceContractRefOnlyTest.class);
        
        assertThat(output).contains("Contract ref: Internal Policy DOC-001");
        assertThat(output).doesNotContain("Threshold origin:");
    }

    @Test
    void bothSet_includedInCorrectOrder() {
        String output = captureTestOutput(ProvenanceWithBothTest.class);
        
        assertThat(output).contains("Threshold origin: SLA");
        assertThat(output).contains("Contract ref: Acme API SLA v3.2 §2.1");
        
        // Verify order: thresholdOrigin before contractRef
        int thresholdOriginIndex = output.indexOf("Threshold origin:");
        int contractRefIndex = output.indexOf("Contract ref:");
        assertThat(thresholdOriginIndex)
                .as("Threshold origin should appear before Contract ref")
                .isLessThan(contractRefIndex);
    }

    @Test
    void slaSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceSlaSourceTest.class);
        assertThat(output).contains("Threshold origin: SLA");
    }

    @Test
    void sloSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceSloSourceTest.class);
        assertThat(output).contains("Threshold origin: SLO");
    }

    @Test
    void policySource_renderedCorrectly() {
        String output = captureTestOutput(ProvenancePolicySourceTest.class);
        assertThat(output).contains("Threshold origin: POLICY");
    }

    @Test
    void empiricalSource_renderedCorrectly() {
        String output = captureTestOutput(ProvenanceEmpiricalSourceTest.class);
        assertThat(output).contains("Threshold origin: EMPIRICAL");
    }

    @Test
    void configurationResolver_extractsProvenance() {
        // Unit test for ConfigurationResolver.ResolvedConfiguration provenance methods
        ConfigurationResolver.ResolvedConfiguration config = 
            new ConfigurationResolver.ResolvedConfiguration(
                100, 0.95, 1.0, 0, 0, 0,
                org.javai.punit.api.BudgetExhaustedBehavior.FAIL,
                org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE,
                5,
                null, null, null, null,
                ThresholdOrigin.SLA, "Test Contract"
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
                5
            );
        
        assertThat(config.hasProvenance()).isFalse();
        assertThat(config.hasThresholdOrigin()).isFalse();
        assertThat(config.hasContractRef()).isFalse();
    }

    @Test
    void configurationResolver_emptyContractRef_hasContractRefFalse() {
        ConfigurationResolver.ResolvedConfiguration config = 
            new ConfigurationResolver.ResolvedConfiguration(
                100, 0.95, 1.0, 0, 0, 0,
                org.javai.punit.api.BudgetExhaustedBehavior.FAIL,
                org.javai.punit.api.ExceptionHandling.FAIL_SAMPLE,
                5,
                null, null, null, null,
                ThresholdOrigin.SLA, ""
            );
        
        assertThat(config.hasProvenance()).isTrue();  // has thresholdOrigin
        assertThat(config.hasThresholdOrigin()).isTrue();
        assertThat(config.hasContractRef()).isFalse();  // empty string
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Captures stdout output from running a test class.
     */
    private String captureTestOutput(Class<?> testClass) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(baos));
            
            EngineTestKit.engine(JUNIT_ENGINE_ID)
                    .selectors(DiscoverySelectors.selectClass(testClass))
                    .execute();
            
            return baos.toString();
        } finally {
            System.setOut(originalOut);
        }
    }
}

