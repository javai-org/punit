package org.javai.punit.junit5;

import org.javai.punit.junit5.testsubjects.PunitSubjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

@DisplayName("@ProbabilisticTest / @Experiment + Punit factories — JUnit-driven typed-engine outcomes")
class PunitJunitIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    // ── @ProbabilisticTest path ────────────────────────────────────

    @Test
    @DisplayName("contractual PASS surfaces as a JUnit pass")
    void contractualPass() {
        Events events = run(PunitSubjects.PassingContractualTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("contractual FAIL surfaces as AssertionFailedError")
    void contractualFail() {
        Events events = run(PunitSubjects.FailingContractualTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable)
                            .isInstanceOf(AssertionFailedError.class);
                    org.assertj.core.api.Assertions.assertThat(throwable.getMessage())
                            .contains("FAIL")
                            .contains("bernoulli-pass-rate");
                });
    }

    @Test
    @DisplayName("empirical INCONCLUSIVE (no baseline resolved) surfaces as TestAbortedException")
    void empiricalInconclusive() {
        Events events = run(PunitSubjects.InconclusiveEmpiricalTest.class);
        events.assertStatistics(stats -> stats.started(1).aborted(1));
        events.aborted()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable)
                            .isInstanceOf(TestAbortedException.class);
                });
    }

    // ── @Experiment path ───────────────────────────────────────────

    @Test
    @DisplayName("@Experiment Punit.measuring(...).run() returns normally")
    void measureExperimentRuns() {
        Events events = run(PunitSubjects.PassingMeasureExperiment.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("@Experiment Punit.exploring(...).grid(...).run() returns normally")
    void exploreExperimentRuns() {
        Events events = run(PunitSubjects.PassingExploreExperiment.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    // ── Empirical-supplier path ────────────────────────────────────

    @Test
    @DisplayName("Punit.testing(supplier).samples(N).criterion(...).assertPasses() derives from baseline")
    void empiricalSupplierDerivesFromBaseline() {
        // Without an on-disk baseline the resolver returns EMPTY, so the
        // criterion yields INCONCLUSIVE → aborted. The path itself works
        // (factors and sampling derived from supplier; only sample count
        // specified at test side).
        Events events = run(PunitSubjects.EmpiricalSupplierTest.class);
        events.assertStatistics(stats -> stats.started(1).aborted(1));
    }

    @Test
    @DisplayName("Punit.testing(supplier) rejects a non-MEASURE supplier with IllegalArgumentException")
    void empiricalSupplierRejectsNonMeasure() {
        Events events = run(PunitSubjects.EmpiricalSupplierBadKindTest.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    var throwable = event.getRequiredPayload(
                            org.junit.platform.engine.TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    org.assertj.core.api.Assertions.assertThat(throwable.getMessage())
                            .contains("MEASURE")
                            .contains("EXPLORE");
                });
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
