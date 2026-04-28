package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.api.typed.spec.BaselineStatistics;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.api.typed.spec.PassRateStatistics;
import org.javai.punit.api.typed.spec.ProbabilisticTest;
import org.javai.punit.api.typed.spec.ProbabilisticTestResult;
import org.javai.punit.api.typed.spec.Verdict;
import org.javai.punit.engine.baseline.BaselineRecord;
import org.javai.punit.engine.baseline.BaselineWriter;
import org.javai.punit.engine.baseline.FactorsFingerprint;
import org.javai.punit.engine.baseline.YamlBaselineProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Empirical criterion end-to-end — Engine + YamlBaselineProvider + BernoulliPassRate.empirical()")
class EmpiricalEndToEndIntegrationTest {

    record Factors(String model, double temperature) { }

    private static final Factors FACTORS = new Factors("gpt-4o", 0.0);
    private static final String USE_CASE_ID = "always-passes-use-case";

    static class AlwaysPassesUseCase implements UseCase<Factors, Integer, Boolean> {
        @Override public UseCaseOutcome<Boolean> apply(Integer input) {
            return UseCaseOutcome.ok(true);
        }
        @Override public String id() { return USE_CASE_ID; }
    }

    private static Sampling<Factors, Integer, Boolean> sampling(int samples) {
        return Sampling.<Factors, Integer, Boolean>builder()
                .useCaseFactory(f -> new AlwaysPassesUseCase())
                .inputs(1, 2, 3)
                .samples(samples)
                .build();
    }

    private static ProbabilisticTest empiricalTest(Sampling<Factors, Integer, Boolean> sampling) {
        return ProbabilisticTest
                .testing(sampling, FACTORS)
                .criterion(BernoulliPassRate.<Boolean>empirical())
                .build();
    }

    /**
     * Writes a baseline whose recorded inputs identity matches what
     * {@link #sampling(int)} produces — the in-process integrity
     * guarantee, restated cross-process. Tests that want to exercise
     * an identity mismatch use {@link #writeBaselineWithMismatchedIdentity}.
     */
    private static void writeBaselineWithPassRate(
            Path baselineDir, double passRate, int sampleCount) throws IOException {
        writeBaseline(baselineDir, passRate, sampleCount, sampling(1).inputsIdentity());
    }

    private static void writeBaselineWithMismatchedIdentity(
            Path baselineDir, double passRate, int sampleCount) throws IOException {
        writeBaseline(baselineDir, passRate, sampleCount, "sha256:other-input-population");
    }

    private static void writeBaseline(
            Path baselineDir, double passRate, int sampleCount,
            String inputsIdentity) throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "measureBaseline", fingerprint,
                inputsIdentity, sampleCount,
                Instant.parse("2026-04-26T15:30:00Z"),
                Map.<String, BaselineStatistics>of(
                        "bernoulli-pass-rate",
                        new PassRateStatistics(passRate, sampleCount)));
        new BaselineWriter().write(record, baselineDir);
    }

    @Test
    @DisplayName("with an on-disk baseline at p = 0.80, an always-passing test produces PASS")
    void empiricalProducesPassWhenObservedExceedsBaseline(@TempDir Path baselineDir)
            throws IOException {
        writeBaselineWithPassRate(baselineDir, 0.80, 1000);

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("origin", "EMPIRICAL");
        assertThat(detail).containsEntry("threshold", 0.80);
        assertThat(detail).containsEntry("baselineSampleCount", 1000);
    }

    @Test
    @DisplayName("with no on-disk baseline, the empirical criterion still yields INCONCLUSIVE — same as the EMPTY provider")
    void empiricalYieldsInconclusiveWhenNoBaselineFile(@TempDir Path emptyDir) {
        var engine = new Engine(new YamlBaselineProvider(emptyDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("baseline");
    }

    @Test
    @DisplayName("without an explicit provider, Engine uses BaselineProvider.EMPTY — empirical criteria yield INCONCLUSIVE")
    void emptyProviderIsTheDefault() {
        // No baselineDir at all — default Engine() falls back to BaselineProvider.EMPTY.
        var result = (ProbabilisticTestResult) new Engine().run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
    }

    @Test
    @DisplayName("with a baseline whose sample count is below the test's, EmpiricalChecks rejects → INCONCLUSIVE")
    void empiricalRejectsWhenTestOutRiguresBaseline(@TempDir Path baselineDir) throws IOException {
        // Test asks for 1000 samples; baseline only has 100.
        writeBaselineWithPassRate(baselineDir, 0.50, 100);

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(1000)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsEntry("testSampleCount", 1000);
        assertThat(detail).containsEntry("baselineSampleCount", 100);
    }

    @Test
    @DisplayName("with a baseline whose recorded inputs identity differs, EmpiricalChecks rejects → INCONCLUSIVE")
    void empiricalRejectsIdentityMismatch(@TempDir Path baselineDir) throws IOException {
        writeBaselineWithMismatchedIdentity(baselineDir, 0.80, 1000);

        var engine = new Engine(new YamlBaselineProvider(baselineDir));
        var result = (ProbabilisticTestResult) engine.run(empiricalTest(sampling(20)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        var detail = result.criterionResults().get(0).result().detail();
        assertThat(detail).containsKey("testInputsIdentity");
        assertThat(detail).containsEntry("baselineInputsIdentity", "sha256:other-input-population");
        assertThat(result.criterionResults().get(0).result().explanation())
                .contains("inputs identity")
                .contains("re-run the baseline measure");
    }

    @Test
    @DisplayName("the empty provider returns Optional.empty for any query")
    void baselineProviderEmptyContract() {
        var resolved = BaselineProvider.EMPTY.baselineFor(
                "any-id", FactorBundle.of(FACTORS),
                "any-criterion", PassRateStatistics.class);

        assertThat(resolved).isEmpty();
    }
}
