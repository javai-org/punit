package org.javai.punit.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.javai.outcome.Outcome;
import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.LatencyResult;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.ServiceContract;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.criterion.Acceptance;
import org.javai.punit.api.criterion.Criteria;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.LatencyStatistics;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.spec.Verdict;
import org.javai.punit.internal.engine.baseline.BaselineRecord;
import org.javai.punit.internal.engine.baseline.BaselineWriter;
import org.javai.punit.internal.engine.baseline.FactorsFingerprint;
import org.javai.punit.internal.engine.baseline.LatencyIndicator;
import org.javai.punit.internal.engine.baseline.YamlBaselineProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end pin for the contract-side latency authoring surface. The
 * contract declares its latency commitment via
 * {@code Acceptance.<O>empirical(P95)} or
 * {@code Acceptance.<O>meeting(SLA).ceiling(P95, ...)}; the paired
 * probabilistic test invokes the contract with no explicit
 * {@code .criterion(...)}, and the framework auto-injects the
 * latency criterion alongside any functional criteria.
 *
 * <p>This pin complements the test-site path covered by
 * {@code EmpiricalLatencyEndToEndIntegrationTest}: the deriver,
 * writer, reader, and evaluator chain is the same — only the
 * authoring surface differs.
 */
@DisplayName("Contract-side latency end-to-end — Acceptance.<O>empirical(...) on the contract")
class ContractLatencyEndToEndIntegrationTest {

    record Factors(String model) { }

    private static final Factors FACTORS = new Factors("model-a");
    private static final String USE_CASE_ID = "contract-latency-pin";

    /** Contract declares its latency commitment on the criteria value. */
    static class LatencyOnContract implements ServiceContract<Factors, Integer, Integer> {
        private final Criteria<Integer> criteria;
        LatencyOnContract(Criteria<Integer> criteria) { this.criteria = criteria; }
        @Override public String id() { return USE_CASE_ID; }
        @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
        @Override public Criteria<Integer> criteria() { return criteria; }
    }

    private static Sampling<Factors, Integer, Integer> sampling(Criteria<Integer> criteria, int samples) {
        return Sampling.<Factors, Integer, Integer>builder()
                .serviceContractFactory(f -> new LatencyOnContract(criteria))
                .inputs(1, 2, 3, 4, 5)
                .samples(samples)
                .build();
    }

    @Test
    @DisplayName("Acceptance.empirical(P95) on contract → auto-injected latency criterion resolves baseline and PASSes")
    void empiricalLatencyOnContractAutoInjects(@TempDir Path baselineDir) throws IOException {
        // Baseline asserts p95 = 1 minute — far above any plausible
        // microbench latency, so observed p95 will be below it.
        writeLatencyBaseline(baselineDir, Duration.ofMinutes(1), 1000);

        Criteria<Integer> criteria = Acceptance.<Integer>empirical(PercentileKey.P95);
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(criteria, 20), FACTORS)
                .build();   // no .criterion(...) — auto-injection from contract

        ProbabilisticTestResult result = (ProbabilisticTestResult)
                new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

        var latencyEntry = result.criterionResults().stream()
                .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "auto-injection failed: no percentile-latency entry on result"));
        assertThat(latencyEntry.result().verdict()).isEqualTo(Verdict.PASS);
        assertThat(latencyEntry.result().detail())
                .containsEntry("origin", "EMPIRICAL")
                .containsEntry("baselineSampleCount", 1000)
                .containsKey("observed.p95")
                .containsEntry("threshold.p95", 60_000L);
    }

    @Test
    @DisplayName("Acceptance.meeting(SLA).ceiling(P95, 500ms) on contract → contractual latency PASSes when observed is below ceiling")
    void contractualLatencyOnContract(@TempDir Path baselineDir) throws IOException {
        // Pass-rate baseline is absent; the latency criterion is
        // contractual so it does not need a baseline.
        Criteria<Integer> criteria = Acceptance.<Integer>meeting(ThresholdOrigin.SLA)
                .ceiling(PercentileKey.P95, Duration.ofMillis(500));
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(criteria, 20), FACTORS)
                .build();

        ProbabilisticTestResult result = (ProbabilisticTestResult)
                new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

        var latencyEntry = result.criterionResults().stream()
                .filter(ec -> "percentile-latency".equals(ec.result().criterionName()))
                .findFirst()
                .orElseThrow();
        assertThat(latencyEntry.result().verdict()).isEqualTo(Verdict.PASS);
        assertThat(latencyEntry.result().detail())
                .containsEntry("origin", "SLA")
                .containsEntry("threshold.p95", 500L)
                .containsKey("observed.p95");
    }

    @Test
    @DisplayName("two latency declarations on a contract → effectiveCriteria() throws")
    void twoLatencyCriteriaRejected() {
        var contract = new ServiceContract<Factors, Integer, Integer>() {
            @Override public String id() { return USE_CASE_ID; }
            @Override public Outcome<Integer> invoke(Integer i, TokenTracker t) { return Outcome.ok(i); }
            @Override public Criteria<Integer> criteria() {
                return Criteria.of(
                        Acceptance.<Integer>empirical(PercentileKey.P95).name("latency-a"),
                        Acceptance.<Integer>empirical(PercentileKey.P99).name("latency-b"));
            }
        };

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(contract::effectiveCriteria)
                .withMessageContaining("at most one")
                .withMessageContaining("latency-a")
                .withMessageContaining("latency-b");
    }

    @Test
    @DisplayName("functional + latency on one contract → both criteria resolved, neither shadows the other")
    void functionalAndLatencyCombine(@TempDir Path baselineDir) throws IOException {
        writeLatencyBaseline(baselineDir, Duration.ofMinutes(1), 1000);

        Criteria<Integer> criteria = Criteria.of(
                Acceptance.<Integer>meeting(0.99, ThresholdOrigin.SLA).name("non-negative")
                        .satisfies("value-not-negative",
                                v -> v >= 0 ? Outcome.ok() : Outcome.fail("neg", "v=" + v)),
                Acceptance.<Integer>empirical(PercentileKey.P95).name("latency-stable"));

        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling(criteria, 20), FACTORS)
                .build();

        ProbabilisticTestResult result = (ProbabilisticTestResult)
                new Engine(new YamlBaselineProvider(baselineDir)).run(spec);

        // Latency criterion auto-injected and resolved.
        assertThat(result.criterionResults().stream()
                .anyMatch(ec -> "percentile-latency".equals(ec.result().criterionName())))
                .as("auto-injection must inject the latency criterion")
                .isTrue();
        // Functional pass-rate criterion auto-injected and resolved.
        assertThat(result.criterionResults().stream()
                .anyMatch(ec -> "bernoulli-pass-rate".equals(ec.result().criterionName())))
                .as("auto-injection must inject the pass-rate criterion alongside latency")
                .isTrue();
    }

    private static void writeLatencyBaseline(
            Path baselineDir, Duration pX, int sampleCount) throws IOException {
        String fingerprint = FactorsFingerprint.of(FactorBundle.of(FACTORS));
        LatencyResult percentiles = new LatencyResult(pX, pX, pX, pX, sampleCount);
        LatencyIndicator indicator = new LatencyIndicator(percentiles, sampleCount, sampleCount);
        Criteria<Integer> dummy = Acceptance.<Integer>empirical(PercentileKey.P95);
        BaselineRecord record = new BaselineRecord(
                USE_CASE_ID, "latencyBaseline", fingerprint,
                sampling(dummy, 1).inputsIdentity(), sampleCount,
                Instant.parse("2026-05-18T12:00:00Z"),
                Map.<String, BaselineStatistics>of(
                        "percentile-latency",
                        new LatencyStatistics(percentiles, sampleCount)),
                org.javai.punit.api.covariate.CovariateProfile.empty(),
                indicator);
        new BaselineWriter().write(record, baselineDir);
    }
}
