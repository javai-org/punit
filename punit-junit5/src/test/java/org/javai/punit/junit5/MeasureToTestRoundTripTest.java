package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.javai.punit.api.Experiment;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.InputSupplier;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.BaselineStatistics;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.javai.punit.engine.baseline.BaselineRecord;
import org.javai.punit.engine.baseline.BaselineWriter;
import org.javai.punit.engine.baseline.FactorsFingerprint;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

@DisplayName("End-to-end: @Experiment writes a baseline; @ProbabilisticTest reads it")
class MeasureToTestRoundTripTest {

    private static final String JUNIT_ENGINE_ID = "junit-jupiter";
    private static final String USE_CASE_ID = "round-trip-use-case";

    private String savedProperty;

    @BeforeEach
    void clearProperty() {
        savedProperty = System.getProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (savedProperty == null) {
            System.clearProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    // ── Emission side: @Experiment writes the baseline file ────────

    @Test
    @DisplayName("Punit.measuring(...).run() writes a baseline YAML when a baseline directory is configured")
    void measureWritesBaseline(@TempDir Path baselineDir) throws IOException {
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());

        Events events = run(MeasureSubjects.PassingMeasure.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        try (Stream<Path> files = Files.list(baselineDir)) {
            assertThat(files.filter(p -> p.toString().endsWith(".yaml")))
                    .as("baseline YAML emitted under %s", baselineDir)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("with no baseline directory configured, .run() succeeds silently — no file emitted")
    void measureSkipsEmissionWithoutDirectory() {
        System.clearProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
        Events events = run(MeasureSubjects.PassingMeasure.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    // ── Resolution side: @ProbabilisticTest reads the baseline ─────

    @Test
    @DisplayName("@ProbabilisticTest finds the baseline at the configured directory and produces a verdict")
    void empiricalReadsHandWrittenBaseline(@TempDir Path baselineDir) throws IOException {
        // Hand-write a baseline at p=0.5 — chosen so the test's Wilson lower
        // bound (on always-passing samples) clears it, producing PASS. Using
        // a baseline-emitted file would tie the test to whatever rate the
        // emitter recorded, and an always-passes use case at n=20 has Wilson
        // lower bound ≈ 0.88 — clears 0.5 comfortably; cannot clear 1.0.
        writeBaselineAt(baselineDir, 0.5, 50);
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());

        Events events = run(MeasureSubjects.EmpiricalAgainstBaseline.class);
        events.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    @DisplayName("full pipeline: emission → resolution → assertion in two JUnit invocations")
    void fullRoundTrip(@TempDir Path baselineDir) throws IOException {
        // Phase 1 — emit a baseline via Punit.measuring(...).run().
        // The use case here passes always; the recorded rate will be 1.0,
        // which is too tight a threshold for a Wilson-bound test to clear.
        // We overwrite with a hand-written 0.5 baseline so Phase 2 can pass.
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, baselineDir.toString());
        Events emitEvents = run(MeasureSubjects.PassingMeasure.class);
        emitEvents.assertStatistics(stats -> stats.started(1).succeeded(1));
        try (Stream<Path> files = Files.list(baselineDir)) {
            assertThat(files.anyMatch(p -> p.toString().endsWith(".yaml")))
                    .as("emission produced a baseline file")
                    .isTrue();
        }

        // Phase 1.5 — overwrite with a baseline whose rate the Wilson bound
        // can clear. Real-world authors would simply observe a lower rate
        // through their use case; this in-test substitution decouples the
        // pipeline-under-test from the use-case design.
        writeBaselineAt(baselineDir, 0.5, 50);

        // Phase 2 — empirical test resolves the baseline and produces PASS.
        Events testEvents = run(MeasureSubjects.EmpiricalAgainstBaseline.class);
        testEvents.assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static void writeBaselineAt(Path dir, double passRate, int sampleCount)
            throws IOException {
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID,
                "hand-written",
                FactorsFingerprint.of(FactorBundle.of(new MeasureSubjects.NoFactors())),
                InputSupplier.from(() -> List.of(1, 2, 3)).identity(),
                sampleCount,
                Instant.parse("2026-04-28T12:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        new PassRateStatistics(passRate, sampleCount)));
        new BaselineWriter().write(record, dir);
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine(JUNIT_ENGINE_ID)
                .selectors(DiscoverySelectors.selectClass(testClass))
                .execute()
                .testEvents();
    }

    /**
     * Subjects co-located with the test (rather than under
     * {@code testsubjects/}) because they share state via the
     * baseline directory configured per-test through a system
     * property — keeping them next to their consumers makes the
     * coupling visible.
     */
    static final class MeasureSubjects {

        record NoFactors() { }

        private static UseCase<NoFactors, Integer, Boolean> useCase() {
            return new UseCase<>() {
                @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                    return UseCaseOutcome.ok(true);
                }
                @Override public String id() { return USE_CASE_ID; }
            };
        }

        private static Sampling<NoFactors, Integer, Boolean> sampling(int samples) {
            return Sampling.<NoFactors, Integer, Boolean>builder()
                    .useCaseFactory(f -> useCase())
                    .inputs(1, 2, 3)
                    .samples(samples)
                    .build();
        }

        public static final class PassingMeasure {
            @Experiment
            void measure() {
                Punit.measuring(sampling(50), new NoFactors())
                        .experimentId("baseline-v1")
                        .run();
            }
        }

        public static final class EmpiricalAgainstBaseline {
            @ProbabilisticTest
            void shouldPass() {
                Punit.testing(sampling(20), new NoFactors())
                        .criterion(BernoulliPassRate.<Boolean>empirical())
                        .assertPasses();
            }
        }
    }
}
