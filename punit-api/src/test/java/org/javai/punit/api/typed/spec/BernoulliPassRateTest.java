package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.LatencyResult;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BernoulliPassRate")
class BernoulliPassRateTest {

    record Factors(String label) {}

    private static SampleSummary<String> summary(int successes, int failures) {
        int total = successes + failures;
        var outcomes = new java.util.ArrayList<UseCaseOutcome<String>>(total);
        for (int i = 0; i < successes; i++) outcomes.add(UseCaseOutcome.ok("ok"));
        for (int i = 0; i < failures; i++) outcomes.add(UseCaseOutcome.fail("nope", "msg"));
        return new SampleSummary<>(
                outcomes,
                Duration.ofMillis(1),
                successes, failures, 0L, 0,
                LatencyResult.empty(),
                TerminationReason.COMPLETED,
                List.of());
    }

    private static final String DEFAULT_IDENTITY = "sha256:test-default-identity";

    private static <OT> EvaluationContext<OT, PassRateStatistics> ctx(
            SampleSummary<OT> summary, Optional<PassRateStatistics> baseline) {
        return ctx(summary, baseline, DEFAULT_IDENTITY,
                baseline.isPresent() ? Optional.of(DEFAULT_IDENTITY) : Optional.empty());
    }

    private static <OT> EvaluationContext<OT, PassRateStatistics> ctx(
            SampleSummary<OT> summary,
            Optional<PassRateStatistics> baseline,
            String testIdentity,
            Optional<String> baselineIdentity) {
        return new EvaluationContext<OT, PassRateStatistics>() {
            @Override public SampleSummary<OT> summary() { return summary; }
            @Override public Optional<PassRateStatistics> baseline() { return baseline; }
            @Override public FactorBundle factors() { return FactorBundle.of(new Factors("x")); }
            @Override public String testInputsIdentity() { return testIdentity; }
            @Override public Optional<String> baselineInputsIdentity() { return baselineIdentity; }
        };
    }

    // ── meeting() — contractual ────────────────────────────────────

    @Test
    @DisplayName("meeting() returns PASS when observed meets threshold")
    void contractualPass() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.meeting(0.9, ThresholdOrigin.SLA);

        CriterionResult result = criterion.evaluate(ctx(summary(95, 5), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.criterionName()).isEqualTo("bernoulli-pass-rate");
        assertThat(result.detail()).containsEntry("origin", "SLA");
        assertThat(result.detail()).containsEntry("threshold", 0.9);
        assertThat((double) result.detail().get("observed")).isEqualTo(0.95);
        assertThat(result.detail()).containsEntry("total", 100);
    }

    @Test
    @DisplayName("meeting() returns FAIL when observed below threshold")
    void contractualFail() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.meeting(0.9, ThresholdOrigin.SLA);

        CriterionResult result = criterion.evaluate(ctx(summary(80, 20), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat((double) result.detail().get("observed")).isEqualTo(0.8);
    }

    @Test
    @DisplayName("meeting() rejects threshold outside [0, 1]")
    void meetingRejectsOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BernoulliPassRate.meeting(-0.1, ThresholdOrigin.SLA));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BernoulliPassRate.meeting(1.1, ThresholdOrigin.SLA));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BernoulliPassRate.meeting(Double.NaN, ThresholdOrigin.SLA));
    }

    @Test
    @DisplayName("meeting() rejects ThresholdOrigin.EMPIRICAL")
    void meetingRejectsEmpiricalOrigin() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> BernoulliPassRate.meeting(0.9, ThresholdOrigin.EMPIRICAL))
                .withMessageContaining("empirical");
    }

    // ── empirical() — default resolution ────────────────────────────

    @Test
    @DisplayName("empirical() with no baseline returns INCONCLUSIVE")
    void empiricalNoBaselineInconclusive() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();

        CriterionResult result = criterion.evaluate(ctx(summary(90, 10), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation()).contains("baseline");
    }

    @Test
    @DisplayName("empirical() with a resolved baseline produces PASS / FAIL per inequality")
    void empiricalWithBaseline() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 2000);

        CriterionResult pass = criterion.evaluate(ctx(summary(90, 10), Optional.of(baseline)));
        CriterionResult fail = criterion.evaluate(ctx(summary(80, 20), Optional.of(baseline)));

        assertThat(pass.verdict()).isEqualTo(Verdict.PASS);
        assertThat(fail.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(pass.detail()).containsEntry("origin", "EMPIRICAL");
        assertThat(pass.detail()).containsEntry("baselineSampleCount", 2000);
        assertThat((double) pass.detail().get("threshold")).isEqualTo(0.88);
    }

    // ── sample-size constraint (test_N ≤ baseline_N) ───────────────

    @Test
    @DisplayName("empirical() with test sample count > baseline returns INCONCLUSIVE")
    void empiricalRejectsTestLargerThanBaseline() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 100);

        // 200 test samples > 100 baseline samples
        CriterionResult result = criterion.evaluate(ctx(summary(180, 20), Optional.of(baseline)));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation())
                .contains("test sample size (200)")
                .contains("baseline sample size (100)")
                .contains("at least as rigorous");
        assertThat(result.detail()).containsEntry("testSampleCount", 200);
        assertThat(result.detail()).containsEntry("baselineSampleCount", 100);
    }

    @Test
    @DisplayName("empirical() with test sample count == baseline proceeds to verdict")
    void empiricalAcceptsEqualSampleCount() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 100);

        CriterionResult result = criterion.evaluate(ctx(summary(95, 5), Optional.of(baseline)));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("empirical() with test sample count < baseline proceeds to verdict")
    void empiricalAcceptsSmallerTestSampleCount() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 1000);

        CriterionResult result = criterion.evaluate(ctx(summary(45, 5), Optional.of(baseline)));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── inputs-identity check ───────────────────────────────────────

    @Test
    @DisplayName("empirical() with mismatched test/baseline inputs identity returns INCONCLUSIVE")
    void empiricalRejectsIdentityMismatch() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 1000);

        CriterionResult result = criterion.evaluate(ctx(
                summary(90, 10), Optional.of(baseline),
                "sha256:test-inputs-A",
                Optional.of("sha256:baseline-inputs-B")));

        assertThat(result.verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(result.explanation())
                .contains("inputs identity")
                .contains("re-run the baseline measure");
        assertThat(result.detail())
                .containsEntry("testInputsIdentity", "sha256:test-inputs-A")
                .containsEntry("baselineInputsIdentity", "sha256:baseline-inputs-B");
    }

    @Test
    @DisplayName("empirical() with matching test/baseline identity proceeds to verdict")
    void empiricalAcceptsMatchingIdentity() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 1000);

        CriterionResult result = criterion.evaluate(ctx(
                summary(90, 10), Optional.of(baseline),
                "sha256:matching-id",
                Optional.of("sha256:matching-id")));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    @Test
    @DisplayName("empirical() identity-mismatch fires before sample-size — identity is the more fundamental violation")
    void identityMismatchPrecedesSampleSize() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.88, 100);

        // Both rules would fire: 200 test samples > 100 baseline AND identities differ.
        CriterionResult result = criterion.evaluate(ctx(
                summary(180, 20), Optional.of(baseline),
                "sha256:test-id", Optional.of("sha256:baseline-id")));

        assertThat(result.explanation()).contains("inputs identity");
        assertThat(result.explanation()).doesNotContain("sample size");
    }

    @Test
    @DisplayName("contractual meeting() does not impose sample-size constraint — no baseline involved")
    void contractualIgnoresSampleSize() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.meeting(0.9, ThresholdOrigin.SLA);

        // Test has 10000 samples; no baseline, so the constraint doesn't apply.
        CriterionResult result = criterion.evaluate(ctx(summary(9500, 500), Optional.empty()));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── atConfidence() ───────────────────────────────────────────────

    @Test
    @DisplayName("atConfidence() returns a new criterion carrying the confidence")
    void atConfidenceRecordedInDetail() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.<String>empirical().atConfidence(0.99);
        PassRateStatistics baseline = new PassRateStatistics(0.9, 1000);

        CriterionResult result = criterion.evaluate(ctx(summary(95, 5), Optional.of(baseline)));

        assertThat(result.detail()).containsEntry("confidence", 0.99);
    }

    @Test
    @DisplayName("atConfidence() default is 0.95 on the empirical variant")
    void atConfidenceDefaultEmpirical() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        PassRateStatistics baseline = new PassRateStatistics(0.9, 1000);

        CriterionResult result = criterion.evaluate(ctx(summary(95, 5), Optional.of(baseline)));

        assertThat(result.detail()).containsEntry("confidence", 0.95);
    }

    @Test
    @DisplayName("atConfidence() rejects values outside (0, 1)")
    void atConfidenceRejectsOutOfRange() {
        BernoulliPassRate<String> c = BernoulliPassRate.empirical();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(0.0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(1.0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(-0.5));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> c.atConfidence(Double.NaN));
    }

    // ── empiricalFrom() — pinned ─────────────────────────────────────

    @Test
    @DisplayName("empiricalFrom() exposes the supplier for framework routing")
    void empiricalFromExposesSupplier() {
        java.util.function.Supplier<Experiment> supplier = () -> null;

        BernoulliPassRate<String> criterion = BernoulliPassRate.empiricalFrom(supplier);

        assertThat(criterion.baselineSupplier()).contains(supplier);
    }

    @Test
    @DisplayName("empiricalFrom() rejects null supplier")
    void empiricalFromRejectsNull() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> BernoulliPassRate.empiricalFrom(null));
    }

    @Test
    @DisplayName("non-empirical variants expose empty baselineSupplier")
    void nonEmpiricalHasEmptySupplier() {
        assertThat(BernoulliPassRate.meeting(0.9, ThresholdOrigin.SLA).baselineSupplier()).isEmpty();
        assertThat(BernoulliPassRate.empirical().baselineSupplier()).isEmpty();
    }

    // ── zero samples ─────────────────────────────────────────────────

    @Test
    @DisplayName("zero samples → INCONCLUSIVE regardless of mode")
    void zeroSamplesInconclusive() {
        BernoulliPassRate<String> c1 = BernoulliPassRate.meeting(0.9, ThresholdOrigin.SLA);
        BernoulliPassRate<String> c2 = BernoulliPassRate.empirical();

        var empty = summary(0, 0);
        assertThat(c1.evaluate(ctx(empty, Optional.empty())).verdict()).isEqualTo(Verdict.INCONCLUSIVE);
        assertThat(c2.evaluate(ctx(empty, Optional.of(new PassRateStatistics(0.9, 1000)))).verdict())
                .isEqualTo(Verdict.INCONCLUSIVE);
    }

    // ── typed plumbing ───────────────────────────────────────────────

    @Test
    @DisplayName("criterion exposes PassRateStatistics.class as its statistics type")
    void statisticsTypeIsPassRateStatistics() {
        BernoulliPassRate<String> criterion = BernoulliPassRate.empirical();
        assertThat(criterion.statisticsType()).isEqualTo(PassRateStatistics.class);
    }

    @Test
    @DisplayName("name() is 'bernoulli-pass-rate'")
    void name() {
        assertThat(BernoulliPassRate.empirical().name()).isEqualTo("bernoulli-pass-rate");
    }
}
