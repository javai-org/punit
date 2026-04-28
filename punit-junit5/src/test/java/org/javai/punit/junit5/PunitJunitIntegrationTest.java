package org.javai.punit.junit5;

import org.javai.punit.junit5.testsubjects.PunitTestSubjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

@DisplayName("@PunitTest + Punit.run — JUnit-driven typed-engine outcomes")
class PunitJunitIntegrationTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @Test
    @DisplayName("PASS verdict surfaces as a JUnit pass")
    void passingTest() {
        Events events = run(PunitTestSubjects.PassingTest.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("FAIL verdict surfaces as a JUnit failure carrying AssertionFailedError")
    void failingTest() {
        Events events = run(PunitTestSubjects.FailingTest.class);
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
    @DisplayName("INCONCLUSIVE verdict surfaces as a JUnit aborted (skipped) test")
    void inconclusiveTest() {
        Events events = run(PunitTestSubjects.InconclusiveEmpiricalTest.class);
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

    @Test
    @DisplayName("Punit.run rejects null spec")
    void rejectsNullSpec() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> Punit.run(null));
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
