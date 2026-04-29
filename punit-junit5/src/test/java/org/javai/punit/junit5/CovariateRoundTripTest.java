package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.javai.punit.junit5.testsubjects.CovariateSubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * End-to-end covariate wiring: a use case declares a custom
 * {@code region} covariate; a measure experiment writes a baseline
 * stamped with the resolved value; an empirical test resolves the
 * matching baseline at evaluate time. Demonstrates that the
 * declaration→resolution→identity→matching loop is closed.
 */
@DisplayName("Covariate end-to-end: MEASURE writes profile, TEST resolves matching baseline")
class CovariateRoundTripTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedProperty;
    private String savedCustomCovariate;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY,
                baselineDir.toString());
        savedCustomCovariate = System.getProperty(CovariateSubjects.REGION_PROPERTY);
        System.setProperty(CovariateSubjects.REGION_PROPERTY, "EU");
    }

    @AfterEach
    void tearDown() {
        restoreProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, savedProperty);
        restoreProperty(CovariateSubjects.REGION_PROPERTY, savedCustomCovariate);
    }

    private static void restoreProperty(String key, String saved) {
        if (saved == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, saved);
        }
    }

    @Test
    @DisplayName("measure stamps the resolved covariate profile into the baseline filename")
    void measureWritesProfileStampedFilename() throws IOException {
        Events events = run(CovariateSubjects.MeasureWithCovariate.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        try (Stream<Path> files = Files.list(baselineDir)) {
            List<Path> emitted = files.toList();
            assertThat(emitted)
                    .as("baseline emitted under %s", baselineDir)
                    .hasSize(1);
            String filename = emitted.get(0).getFileName().toString();
            // EX09: filename ends with -{covHash}.yaml when a covariate
            // is declared. The exact hash is environment-derived; we
            // pin shape, not value.
            assertThat(filename)
                    .startsWith(CovariateSubjects.USE_CASE_ID + ".measureBaseline-")
                    // Factors fingerprint is 8 hex chars; one declared
                    // covariate adds a -{4-hex} tail per EX09.
                    .matches(".*-[0-9a-f]{8}-[0-9a-f]{4}\\.yaml$");
        }
    }

    @Test
    @DisplayName("test under matching covariate resolves the just-written baseline")
    void testResolvesMatchingBaseline() throws IOException {
        // Phase 1: write a baseline under region=EU.
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        // Phase 2: empirical test under same region=EU resolves it.
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);
        testEvents.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("test under non-matching covariate produces INCONCLUSIVE (no matching baseline)")
    void testUnderDifferentCovariateInconclusive() throws IOException {
        // Phase 1: write baseline under region=EU.
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        // Phase 2: switch region to APAC; no baseline under that
        // partition exists, the empty-profile fallback isn't there
        // either → INCONCLUSIVE → JUnit aborted (skipped) test.
        System.setProperty(CovariateSubjects.REGION_PROPERTY, "APAC");
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);
        testEvents.assertStatistics(stats -> stats.started(1).aborted(1));
    }

    @Test
    @DisplayName("misalignment surfaces in the JUnit abort message — author sees why")
    void misalignmentExplainedInVerdict() throws IOException {
        run(CovariateSubjects.MeasureWithCovariate.class)
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        System.setProperty(CovariateSubjects.REGION_PROPERTY, "APAC");
        Events testEvents = run(CovariateSubjects.TestWithMatchingCovariate.class);

        // The aborted-event payload carries the diagnostic that the
        // empirical Punit.testing(...) translates from the test result.
        // It should mention the rejection reason for the EU baseline.
        testEvents.aborted()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable.getMessage())
                            .contains("CONFIGURATION mismatch on region")
                            .contains("current=APAC")
                            .contains("baseline=EU");
                });
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
