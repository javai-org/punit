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

    private static <OT> EvaluationContext<OT, PassRateStatistics> ctx(
            SampleSummary<OT> summary, Optional<PassRateStatistics> baseline) {
        return new EvaluationContext<OT, PassRateStatistics>() {
            @Override public SampleSummary<OT> summary() { return summary; }
            @Override public Optional<PassRateStatistics> baseline() { return baseline; }
            @Override public FactorBundle factors() { return FactorBundle.of(new Factors("x")); }
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
        java.util.function.Supplier<MeasureSpec<?, ?, ?>> supplier = () -> null;

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
