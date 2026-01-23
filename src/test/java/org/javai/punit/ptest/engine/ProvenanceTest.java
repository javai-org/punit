package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.reporting.PUnitReporter;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.AlwaysPassingTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceContractRefOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceEmpiricalSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenancePolicySourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSlaSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceSloSourceTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceThresholdOriginOnlyTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceUnspecifiedTest;
import org.javai.punit.testsubjects.ProbabilisticTestSubjects.ProvenanceWithBothTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    private static final String PUNIT_REPORTER_LOGGER = PUnitReporter.class.getName();
    
    private TestAppender testAppender;
    private LoggerConfig targetLoggerConfig;
    
    @BeforeEach
    void setUp() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        
        // Get or create the PUnitReporter logger config
        targetLoggerConfig = config.getLoggerConfig(PUNIT_REPORTER_LOGGER);
        if (!targetLoggerConfig.getName().equals(PUNIT_REPORTER_LOGGER)) {
            targetLoggerConfig = new LoggerConfig(PUNIT_REPORTER_LOGGER, Level.INFO, false);
            config.addLogger(PUNIT_REPORTER_LOGGER, targetLoggerConfig);
        }
        
        testAppender = new TestAppender("TestAppender");
        testAppender.start();
        targetLoggerConfig.addAppender(testAppender, Level.INFO, null);
        ctx.updateLoggers();
    }
    
    @AfterEach
    void tearDown() {
        if (testAppender != null) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            targetLoggerConfig.removeAppender(testAppender.getName());
            testAppender.stop();
            ctx.updateLoggers();
        }
    }

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
     * Captures Log4j output from running a test class.
     */
    private String captureTestOutput(Class<?> testClass) {
        testAppender.clear();
        
        EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute();
        
        return testAppender.getOutput();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEST APPENDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * A simple Log4j appender that captures log messages for testing.
     */
    private static class TestAppender extends AbstractAppender {
        
        private final List<String> messages = new ArrayList<>();
        
        protected TestAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }
        
        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
        
        public void clear() {
            messages.clear();
        }
        
        public String getOutput() {
            return String.join("\n", messages);
        }
    }
}

