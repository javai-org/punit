package org.javai.punit.api.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.Contract;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.LatencySpec;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PercentileLatency")
class PercentileLatencyTest {

    record Factors(String label) {}

    private static final Contract<Object, String> STUB_CONTRACT = new Contract<>() {
        @Override public Outcome<String> invoke(Object input, TokenTracker tracker) {
            return Outcome.ok("ok");
        }
        @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
    };

    private static SampleSummary<String> summary(LatencyResult latency, int successes, int failures) {
        int total = successes + failures;
        var outcomes = new java.util.ArrayList<UseCaseOutcome<?, String>>(total);
        for (int i = 0; i < successes; i++) outcomes.add(stubOutcome(Outcome.ok("ok")));
        for (int i = 0; i < failures; i++) outcomes.add(stubOutcome(Outcome.fail("nope", "msg")));
        return new SampleSummary<>(
                outcomes,
                Duration.ofMillis(1),
                successes, failures, 0L, 0,
                latency,
                TerminationReason.COMPLETED,
                List.of());
    }

    private static UseCaseOutcome<Object, String> stubOutcome(Outcome<String> result) {
        return new UseCaseOutcome<>(
                result, STUB_CONTRACT,
                List.of(), Optional.empty(),
                0L, Duration.ZERO);
    }

    private static LatencyResult observed(long p50, long p90, long p95, long p99, int n) {
        return new LatencyResult(
                Duration.ofMillis(p50),
                Duration.ofMillis(p90),
                Duration.ofMillis(p95),
                Duration.ofMillis(p99),
                n);
    }

    private static final String DEFAULT_IDENTITY = "sha256:test-default-identity";

    private static <OT> EvaluationContext<OT, LatencyStatistics> ctx(
            SampleSummary<OT> summary, Optional<LatencyStatistics> baseline) {
        return ctx(summary, baseline, DEFAULT_IDENTITY,
                baseline.isPresent() ? Optional.of(DEFAULT_IDENTITY) : Optional.empty());
    }

    private static <OT> EvaluationContext<OT, LatencyStatistics> ctx(
            SampleSummary<OT> summary,
            Optional<LatencyStatistics> baseline,
            String testIdentity,
            Optional<String> baselineIdentity) {
        return new EvaluationContext<OT, LatencyStatistics>() {
            @Override public SampleSummary<OT> summary() { return summary; }
            @Override public Optional<LatencyStatistics> baseline() { return baseline; }
            @Override public FactorBundle factors() { return FactorBundle.of(new Factors("x")); }
            @Override public String testInputsIdentity() { return testIdentity; }
            @Override public Optional<String> baselineInputsIdentity() { return baselineIdentity; }
        };
    }

    // ── meeting() — contractual ────────────────────────────────────

    @Test
    @DisplayName("meeting() returns PASS when every asserted percentile is under the ceiling")
    void contractualPass() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).p99Millis(1000).build();
        PercentileLatency<String> criterion = PercentileLatency.meeting(spec, ThresholdOrigin.SLA);

        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(100, 200, 450, 900, 1000), 1000, 0), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.detail()).containsEntry("assertedPercentiles", "p95,p99");
        assertThat(result.detail()).containsEntry("origin", "SLA");
        assertThat(result.detail()).containsEntry("threshold.p95", 500L);
        assertThat(result.detail()).containsEntry("threshold.p99", 1000L);
        assertThat(result.detail()).containsEntry("observed.p95", 450L);
        assertThat(result.detail()).containsEntry("observed.p99", 900L);
    }

    @Test
    @DisplayName("meeting() returns FAIL with breach entries for exceeded percentiles")
    void contractualFailWithBreaches() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).p99Millis(1000).build();
        PercentileLatency<String> criterion = PercentileLatency.meeting(spec, ThresholdOrigin.SLA);

        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(100, 200, 600, 1200, 1000), 1000, 0), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.detail()).containsEntry("breach.p95", 600L);
        assertThat(result.detail()).containsEntry("breach.p99", 1200L);
        assertThat(result.explanation()).contains("p95");
        assertThat(result.explanation()).contains("p99");
    }

    @Test
    @DisplayName("meeting() rejects a LatencySpec with no asserted percentiles")
    void meetingRejectsEmptyLatencySpec() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PercentileLatency.meeting(LatencySpec.disabled(), ThresholdOrigin.SLA))
                .withMessageContaining("at least one percentile");
    }

    @Test
    @DisplayName("meeting() rejects ThresholdOrigin.EMPIRICAL")
    void meetingRejectsEmpiricalOrigin() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).build();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PercentileLatency.meeting(spec, ThresholdOrigin.EMPIRICAL))
                .withMessageContaining("empirical");
    }

    // ── empirical() ────────────────────────────────────────────────

    @Test
    @DisplayName("empirical() with no baseline returns INCONCLUSIVE")
    void empiricalNoBaselineInconclusive() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95, PercentileKey.P99);

        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(100, 200, 400, 900, 1000), 1000, 0), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.detail()).containsEntry("assertedPercentiles", "p95,p99");
    }

    @Test
    @DisplayName("empirical() with baseline produces PASS / FAIL per observed vs baseline")
    void empiricalWithBaseline() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95, PercentileKey.P99);
        LatencyStatistics baseline = new LatencyStatistics(observed(100, 200, 500, 1000, 2000), 2000);

        CriterionResult pass = criterion.evaluate(
                ctx(summary(observed(80, 180, 480, 950, 1000), 1000, 0), Optional.of(baseline)));
        CriterionResult fail = criterion.evaluate(
                ctx(summary(observed(80, 180, 550, 1100, 1000), 1000, 0), Optional.of(baseline)));

        assertThat(pass.verdict()).isEqualTo(Verdict.PASS);
        assertThat(fail.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(pass.detail()).containsEntry("origin", "EMPIRICAL");
        assertThat(pass.detail()).containsEntry("baselineSampleCount", 2000);
        assertThat(fail.detail()).containsEntry("breach.p95", 550L);
        assertThat(fail.detail()).containsEntry("breach.p99", 1100L);
    }

    // ── sample-size constraint (test_N ≤ baseline_N) ───────────────

    @Test
    @DisplayName("empirical() with test sample count > baseline returns INCONCLUSIVE")
    void empiricalRejectsTestLargerThanBaseline() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        LatencyStatistics baseline = new LatencyStatistics(observed(100, 200, 500, 1000, 2000), 100);

        // 1000 test samples > 100 baseline samples
        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(80, 180, 480, 950, 1000), 1000, 0), Optional.of(baseline)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation())
                .contains("test sample size (1000)")
                .contains("baseline sample size (100)")
                .contains("at least as rigorous");
        assertThat(result.detail()).containsEntry("testSampleCount", 1000);
        assertThat(result.detail()).containsEntry("baselineSampleCount", 100);
    }

    @Test
    @DisplayName("empirical() with test sample count ≤ baseline proceeds to verdict")
    void empiricalAcceptsSmallerOrEqualTestSampleCount() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        LatencyStatistics baseline = new LatencyStatistics(observed(100, 200, 500, 1000, 2000), 1000);

        CriterionResult equal = criterion.evaluate(
                ctx(summary(observed(80, 180, 480, 950, 1000), 1000, 0), Optional.of(baseline)));
        CriterionResult smaller = criterion.evaluate(
                ctx(summary(observed(80, 180, 480, 950, 500), 500, 0), Optional.of(baseline)));

        assertThat(equal.verdict()).isEqualTo(Verdict.PASS);
        assertThat(smaller.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── inputs-identity check ───────────────────────────────────────

    @Test
    @DisplayName("empirical() with mismatched test/baseline inputs identity returns INCONCLUSIVE")
    void empiricalRejectsIdentityMismatch() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        LatencyStatistics baseline = new LatencyStatistics(observed(100, 200, 500, 1000, 1000), 1000);

        CriterionResult result = criterion.evaluate(ctx(
                summary(observed(80, 180, 480, 950, 200), 200, 0), Optional.of(baseline),
                "sha256:test-id", Optional.of("sha256:baseline-id")));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation()).contains("inputs identity");
        assertThat(result.detail())
                .containsEntry("testInputsIdentity", "sha256:test-id")
                .containsEntry("baselineInputsIdentity", "sha256:baseline-id")
                .containsEntry("assertedPercentiles", "p95");
    }

    @Test
    @DisplayName("empirical() with matching test/baseline identity proceeds to verdict")
    void empiricalAcceptsMatchingIdentity() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        LatencyStatistics baseline = new LatencyStatistics(observed(100, 200, 500, 1000, 1000), 1000);

        CriterionResult result = criterion.evaluate(ctx(
                summary(observed(80, 180, 480, 950, 200), 200, 0), Optional.of(baseline),
                "sha256:matching", Optional.of("sha256:matching")));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("contractual meeting() does not impose sample-size constraint — no baseline involved")
    void contractualLatencyIgnoresSampleSize() {
        PercentileLatency<String> criterion = PercentileLatency.meeting(
                LatencySpec.builder().p95Millis(500).build(), ThresholdOrigin.SLA);

        // Test has 10000 samples; no baseline, so the constraint doesn't apply.
        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(80, 180, 480, 950, 10000), 10000, 0), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("empirical() deduplicates repeated keys")
    void empiricalDeduplicates() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(
                PercentileKey.P95, PercentileKey.P95, PercentileKey.P99);
        LatencyStatistics baseline = new LatencyStatistics(observed(100, 200, 500, 1000, 2000), 2000);

        CriterionResult result = criterion.evaluate(
                ctx(summary(observed(80, 180, 480, 950, 1000), 1000, 0), Optional.of(baseline)));

        assertThat(result.detail()).containsEntry("assertedPercentiles", "p95,p99");
    }

    @Test
    @DisplayName("empirical() rejects null first key")
    void empiricalRejectsNullFirst() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PercentileLatency.empirical(null));
    }

    // ── empiricalFrom() — pinned ───────────────────────────────────

    @Test
    @DisplayName("empiricalFrom() exposes the supplier for framework routing")
    void empiricalFromExposesSupplier() {
        java.util.function.Supplier<Experiment> supplier = () -> null;

        PercentileLatency<String> criterion = PercentileLatency.empiricalFrom(
                supplier, PercentileKey.P95);

        assertThat(criterion.baselineSupplier()).contains(supplier);
    }

    @Test
    @DisplayName("empiricalFrom() rejects null supplier")
    void empiricalFromRejectsNullSupplier() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PercentileLatency.empiricalFrom(null, PercentileKey.P95));
    }

    @Test
    @DisplayName("non-pinned variants expose empty baselineSupplier")
    void nonPinnedHasEmptySupplier() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).build();
        assertThat(PercentileLatency.meeting(spec, ThresholdOrigin.SLA).baselineSupplier()).isEmpty();
        assertThat(PercentileLatency.empirical(PercentileKey.P95).baselineSupplier()).isEmpty();
    }

    // ── zero samples ───────────────────────────────────────────────

    @Test
    @DisplayName("zero samples → INCONCLUSIVE regardless of mode")
    void zeroSamplesInconclusive() {
        LatencySpec spec = LatencySpec.builder().p95Millis(500).build();
        PercentileLatency<String> c1 = PercentileLatency.meeting(spec, ThresholdOrigin.SLA);
        PercentileLatency<String> c2 = PercentileLatency.empirical(PercentileKey.P95);

        assertThat(c1.evaluate(ctx(summary(LatencyResult.empty(), 0, 0), Optional.empty())).verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(c2.evaluate(ctx(summary(LatencyResult.empty(), 0, 0),
                Optional.of(new LatencyStatistics(observed(100, 200, 500, 1000, 2000), 2000)))).verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
    }

    // ── typed plumbing ───────────────────────────────────────────────

    @Test
    @DisplayName("criterion exposes LatencyStatistics.class as its statistics type")
    void statisticsTypeIsLatencyStatistics() {
        PercentileLatency<String> criterion = PercentileLatency.empirical(PercentileKey.P95);
        assertThat(criterion.statisticsType()).isEqualTo(LatencyStatistics.class);
    }

    @Test
    @DisplayName("name() is 'percentile-latency'")
    void name() {
        assertThat(PercentileLatency.empirical(PercentileKey.P95).name()).isEqualTo("percentile-latency");
    }
}
