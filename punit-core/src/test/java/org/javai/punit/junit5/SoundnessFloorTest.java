package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.javai.punit.internal.engine.baseline.BaselineResolver;
import org.javai.punit.junit5.testsubjects.SoundnessFloorSubjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Audits the soundness floor — the minimum confidence level the
 * framework will accept on a probabilistic test. The floor is the
 * one feasibility check that is <em>not</em> intent-gated: a Smoke
 * test below the floor aborts just as a Verification test below the
 * floor aborts. A test that cannot make a claim at the floor's
 * confidence level cannot underwrite a verdict, regardless of how
 * the developer described their intent.
 *
 * <p>Each abort-shaped test verifies the engine never ran via a
 * counter probe on the service contract's {@code invoke}.
 */
@DisplayName("Soundness floor — confidence-floor enforcement, not intent-gated")
class SoundnessFloorTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";

    @TempDir Path baselineDir;
    private String savedProperty;

    @BeforeEach
    void setUp() {
        savedProperty = System.getProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
        SoundnessFloorSubjects.INVOKE_COUNT.set(0);
    }

    @AfterEach
    void tearDown() {
        if (savedProperty == null) {
            System.clearProperty(BaselineResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    @Test
    @DisplayName("VERIFICATION + confidence below floor → abort, no samples")
    void verificationBelowFloorAborts() {
        Events events = run(SoundnessFloorSubjects.VerificationBelowFloor.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        assertSoundnessFloorBreach(events);
        assertThat(SoundnessFloorSubjects.INVOKE_COUNT.get())
                .as("floor abort must precede sampling — engine never runs")
                .isZero();
    }

    @Test
    @DisplayName("SMOKE + confidence below floor → abort, no samples (floor is not intent-gated)")
    void smokeBelowFloorAborts() {
        Events events = run(SoundnessFloorSubjects.SmokeBelowFloor.class);
        events.assertStatistics(stats -> stats.started(1).failed(1));
        assertSoundnessFloorBreach(events);
        assertThat(SoundnessFloorSubjects.INVOKE_COUNT.get())
                .as("Smoke does not buy past the floor — engine never runs")
                .isZero();
    }

    @Test
    @DisplayName("VERIFICATION + confidence at the floor boundary (0.80) → no floor abort")
    void verificationAtFloorBoundaryRuns() {
        Events events = run(SoundnessFloorSubjects.VerificationAtFloorBoundary.class);
        events.assertStatistics(stats -> stats.started(1));
        long terminal = events.failed().count() + events.succeeded().count();
        assertThat(terminal)
                .as("at the floor boundary the test runs to a verdict, not aborted by the floor")
                .isEqualTo(1);
        assertThat(SoundnessFloorSubjects.INVOKE_COUNT.get())
                .as("engine runs the configured number of samples")
                .isPositive();
    }

    @Test
    @DisplayName("VERIFICATION at framework default confidence → no floor abort")
    void verificationAtFrameworkDefaultRuns() {
        Events events = run(SoundnessFloorSubjects.VerificationAtFrameworkDefault.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1));
        assertThat(SoundnessFloorSubjects.INVOKE_COUNT.get())
                .as("engine runs the configured number of samples")
                .isPositive();
    }

    private static void assertSoundnessFloorBreach(Events events) {
        events.failed()
                .assertThatEvents()
                .anySatisfy(event -> {
                    Throwable t = event.getRequiredPayload(TestExecutionResult.class)
                            .getThrowable().orElseThrow();
                    assertThat(t).isInstanceOf(IllegalStateException.class);
                    assertThat(t.getMessage())
                            .contains("INFEASIBLE: confidence below soundness floor")
                            .contains(SoundnessFloorSubjects.USE_CASE_ID)
                            .contains("80%")
                            .contains("Raise confidence")
                            .contains(".atConfidence(0.95)");
                });
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }
}
