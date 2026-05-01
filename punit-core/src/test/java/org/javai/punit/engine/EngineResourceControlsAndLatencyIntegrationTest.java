package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.javai.outcome.Outcome;
import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.ContractBuilder;
import org.javai.punit.api.LatencySpec;
import org.javai.punit.api.Pacing;
import org.javai.punit.api.Sampling;
import org.javai.punit.api.TokenTracker;
import org.javai.punit.api.UseCase;
import org.javai.punit.engine.criteria.BernoulliPassRate;
import org.javai.punit.api.spec.BudgetExhaustionPolicy;
import org.javai.punit.api.spec.CriterionRole;
import org.javai.punit.api.spec.EvaluatedCriterion;
import org.javai.punit.api.spec.ExceptionPolicy;
import org.javai.punit.api.spec.Experiment;
import org.javai.punit.api.spec.PercentileLatency;
import org.javai.punit.api.spec.ProbabilisticTest;
import org.javai.punit.api.spec.ProbabilisticTestResult;
import org.javai.punit.api.spec.TerminationReason;
import org.javai.punit.api.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end engine behaviour: budget enforcement, pacing, exception
 * policy, latency computation, two-dimensional verdicts.
 *
 * <p>No JUnit extensions, no annotation scanning — these tests
 * construct specs directly and drive them through {@link Engine}.
 */
@DisplayName("Engine resource controls + latency integration")
class EngineResourceControlsAndLatencyIntegrationTest {

    record Factors(String model) {}

    /** Deterministic duration-scripted use case. */
    private static final class ScriptedLatencyUseCase implements UseCase<Factors, Integer, Integer> {
        private final long[] scriptMillis;
        private int index = 0;

        ScriptedLatencyUseCase(long... scriptMillis) {
            this.scriptMillis = scriptMillis;
        }

        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
            long millis = scriptMillis[index % scriptMillis.length];
            index++;
            sleep(millis);
            return Outcome.ok(input);
        }
    }

    /** Sleeps a fixed amount and reports a fixed token cost. */
    private static final class SleepyUseCase implements UseCase<Factors, Integer, Integer> {
        private final long sleepMillis;
        private final long tokens;

        SleepyUseCase(long sleepMillis, long tokens) {
            this.sleepMillis = sleepMillis;
            this.tokens = tokens;
        }

        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
            sleep(sleepMillis);
            tracker.recordTokens(tokens);
            return Outcome.ok(input);
        }
    }

    // ── 1. Time budget stops sampling early ─────────────────────

    @Test
    @DisplayName("time budget terminates sampling early with TIME_BUDGET marker")
    void timeBudgetTerminatesEarly() {
        UseCase<Factors, Integer, Integer> sleeper = new SleepyUseCase(100, 0);
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> sleeper)
                .inputs(1, 2, 3)
                .samples(1000)
                .timeBudget(Duration.ofMillis(500))
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isLessThanOrEqualTo(7);
        assertThat(summary.terminationReason()).isEqualTo(TerminationReason.TIME_BUDGET);
    }

    // ── 2. Token budget stops sampling early ────────────────────

    @Test
    @DisplayName("token budget terminates sampling early with TOKEN_BUDGET marker")
    void tokenBudgetTerminatesEarly() {
        // Declaring a static per-sample charge of 100 matches the
        // BudgetTracker's pre-sample projection: after 2 samples the
        // running total is 200, and projected 200 + 100 > 250 aborts
        // the 3rd before it runs. A use case that reports 100 tokens
        // per outcome would be accounted post-sample; the *projection*
        // is static, so this scenario uses tokenCharge to model it.
        UseCase<Factors, Integer, Integer> zeroOutcomeTokens = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> zeroOutcomeTokens)
                .inputs(1)
                .samples(100)
                .tokenBudget(250)
                .tokenCharge(100)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.tokensConsumed()).isEqualTo(200L);
        assertThat(summary.terminationReason()).isEqualTo(TerminationReason.TOKEN_BUDGET);
    }

    // ── 3. Static charge contributes to budget ─────────────────

    @Test
    @DisplayName("static token charge is accounted for each sample")
    void staticChargeAccountedEachSample() {
        UseCase<Factors, Integer, Integer> zeroCost = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> zeroCost)
                .inputs(1)
                .samples(5)
                .tokenCharge(50)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(5);
        assertThat(summary.tokensConsumed()).isEqualTo(50L * 5);
    }

    // ── 4. PASS_INCOMPLETE surfaces partial result ─────────────

    @Test
    @DisplayName("budget exhaustion produces a summary the spec can detect (terminatedEarly)")
    void passIncompleteSurfacesPartialResult() {
        UseCase<Factors, Integer, Integer> sleeper = new SleepyUseCase(50, 0);
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> sleeper)
                .inputs(1)
                .samples(100)
                .timeBudget(Duration.ofMillis(200))
                .onBudgetExhausted(BudgetExhaustionPolicy.PASS_INCOMPLETE)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.terminatedEarly()).isTrue();
        assertThat(summary.total()).isBetween(2, 7);
    }

    // ── 5 & 6. Pacing inserts inter-sample delay ────────────────

    @Test
    @DisplayName("maxRequestsPerSecond inserts inter-sample delay")
    void maxRpsInsertsDelay() {
        // 10 RPS → 100ms min delay between samples.
        UseCase<Factors, Integer, Integer> pacingUc = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
            @Override public Pacing pacing() {
                return Pacing.builder().maxRequestsPerSecond(10.0).build();
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> pacingUc)
                .inputs(1)
                .samples(5)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        long t0 = System.nanoTime();
        new Engine().run(spec);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        // 5 samples, 4 inter-sample gaps at ≥100ms each → ≥ ~400ms.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(380);
    }

    @Test
    @DisplayName("minMillisPerSample composes with maxRequestsPerSecond via most-restrictive-wins")
    void mostRestrictiveWinsComposition() {
        // maxRps implies 100ms; min explicit 250ms should dominate.
        UseCase<Factors, Integer, Integer> pacingUc = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
                return Outcome.ok(input);
            }
            @Override public Pacing pacing() {
                return Pacing.builder()
                        .maxRequestsPerSecond(10.0)
                        .minMillisPerSample(250L)
                        .build();
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> pacingUc)
                .inputs(1)
                .samples(3)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        long t0 = System.nanoTime();
        new Engine().run(spec);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        // 3 samples, 2 gaps at ≥250ms each → ≥ ~500ms.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(480);
    }

    // ── 7 & 8. Exception policy paths ───────────────────────────────

    @Test
    @DisplayName("FAIL_SAMPLE catches a thrown defect and counts it as a failed sample")
    void failSampleCatchesAndCounts() {
        AtomicInteger n = new AtomicInteger();
        UseCase<Factors, Integer, Integer> flaky = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
                int i = n.incrementAndGet();
                if (i % 2 == 0) {
                    throw new IllegalStateException("scripted defect " + i);
                }
                return Outcome.ok(input);
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> flaky)
                .inputs(1)
                .samples(10)
                .onException(ExceptionPolicy.FAIL_SAMPLE)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(10);
        assertThat(summary.successes()).isEqualTo(5);
        assertThat(summary.failures()).isEqualTo(5);
    }

    @Test
    @DisplayName("ABORT_TEST (default) rethrows a thrown defect")
    void abortTestRethrows() {
        UseCase<Factors, Integer, Integer> defective = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
                throw new IllegalStateException("never mind the exception policy, this is a defect");
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> defective)
                .inputs(1)
                .samples(5)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        assertThatThrownBy(() -> new Engine().run(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("defect");
    }

    // ── 9. maxExampleFailures ──────────────────────────────────

    @Test
    @DisplayName("maxExampleFailures caps retained detail but not failure counts")
    void maxExampleFailuresCaps() {
        UseCase<Factors, Integer, Integer> failing = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
            @Override public Outcome<Integer> invoke(Integer input, TokenTracker tracker) {
                return Outcome.fail("scripted", "boom");
            }
        };
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> failing)
                .inputs(1)
                .samples(50)
                .maxExampleFailures(3)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(50);
        assertThat(summary.failures()).isEqualTo(50);
        assertThat(summary.failuresDropped()).isEqualTo(47);
        // 3 failures retained in outcomes (no successes to swell the list)
        assertThat(summary.outcomes()).hasSize(3);
    }

    // ── 10. Latency percentile computation ─────────────────────

    @Test
    @DisplayName("latency percentiles computed from observed durations (nearest-rank)")
    void latencyPercentilesComputed() {
        // Scripted sleeps 10/20/30/40/50ms.
        UseCase<Factors, Integer, Integer> scripted = new ScriptedLatencyUseCase(10L, 20L, 30L, 40L, 50L);
        Sampling<Factors, Integer, Integer> sampling = Sampling
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> scripted)
                .inputs(1)
                .samples(5)
                .build();
        Experiment spec = Experiment.measuring(sampling, new Factors("m")).build();

        new Engine().run(spec);
        var summary = spec.lastSummary().orElseThrow();
        var lr = summary.latencyResult();
        // Nearest-rank: p50 index = ceil(0.5*5)-1 = 2 -> 30ms; p90 = ceil(4.5)-1 = 4 -> 50ms.
        assertThat(lr.sampleCount()).isEqualTo(5);
        assertThat(lr.p50()).isGreaterThanOrEqualTo(Duration.ofMillis(28));
        assertThat(lr.p50()).isLessThanOrEqualTo(Duration.ofMillis(60));
        assertThat(lr.p90()).isGreaterThanOrEqualTo(Duration.ofMillis(48));
    }

    // ── 11. PercentileLatency contractual breach → FAIL ─────────────

    @Test
    @DisplayName("PercentileLatency.meeting() produces FAIL with breach detail when observed exceeds ceiling")
    void percentileLatencyBreachProducesFail() {
        // 50ms sleeps, assert p50 ≤ 10ms.
        UseCase<Factors, Integer, Boolean> slow = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                sleep(50);
                return Outcome.ok(Boolean.TRUE);
            }
        };
        Sampling<Factors, Integer, Boolean> sampling = Sampling
                .<Factors, Integer, Boolean>builder()
                .useCaseFactory(f -> slow)
                .inputs(1)
                .samples(5)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new Factors("m"))
                .criterion(PercentileLatency.<Boolean>meeting(
                        LatencySpec.builder().p50Millis(10L).build(),
                        ThresholdOrigin.SLA))
                .build();

        var result = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        EvaluatedCriterion entry = result.criterionResults().get(0);
        assertThat(entry.role()).isEqualTo(CriterionRole.REQUIRED);
        assertThat(entry.result().verdict()).isEqualTo(Verdict.FAIL);
        assertThat(entry.result().detail()).containsKey("breach.p50");
    }

    // ── 12. Mixed criteria: functional pass + latency fail  ─────────

    @Test
    @DisplayName("mixed criteria: functional PASS + latency FAIL composes to FAIL when both REQUIRED")
    void mixedCriteriaBothRequiredComposesToFail() {
        UseCase<Factors, Integer, Boolean> slow = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                sleep(50);
                return Outcome.ok(Boolean.TRUE);
            }
        };
        Sampling<Factors, Integer, Boolean> sampling = Sampling
                .<Factors, Integer, Boolean>builder()
                .useCaseFactory(f -> slow)
                .inputs(1)
                .samples(5)
                .build();

        ProbabilisticTest both = ProbabilisticTest
                .testing(sampling, new Factors("m"))
                .criterion(BernoulliPassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLA))
                .criterion(PercentileLatency.<Boolean>meeting(
                        LatencySpec.builder().p50Millis(10L).build(),
                        ThresholdOrigin.SLA))
                .build();
        var rBoth = (ProbabilisticTestResult) new Engine().run(both);
        assertThat(rBoth.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(rBoth.criterionResults()).hasSize(2);
    }

    @Test
    @DisplayName("reportOnly latency: functional PASS + latency FAIL(report-only) composes to PASS")
    void reportOnlyLatencyExcludedFromComposition() {
        UseCase<Factors, Integer, Boolean> slow = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                sleep(50);
                return Outcome.ok(Boolean.TRUE);
            }
        };
        Sampling<Factors, Integer, Boolean> sampling = Sampling
                .<Factors, Integer, Boolean>builder()
                .useCaseFactory(f -> slow)
                .inputs(1)
                .samples(5)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new Factors("m"))
                .criterion(BernoulliPassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLA))
                .reportOnly(PercentileLatency.<Boolean>meeting(
                        LatencySpec.builder().p50Millis(10L).build(),
                        ThresholdOrigin.SLA))
                .build();

        var r = (ProbabilisticTestResult) new Engine().run(spec);
        assertThat(r.verdict()).isEqualTo(Verdict.PASS);
        assertThat(r.criterionResults()).hasSize(2);
        // Latency result is attached with REPORT_ONLY role
        EvaluatedCriterion latency = r.criterionResults().get(1);
        assertThat(latency.role()).isEqualTo(CriterionRole.REPORT_ONLY);
        assertThat(latency.result().verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    @DisplayName("reportOnly functional: contract failures reported but excluded → latency criterion determines verdict")
    void reportOnlyFunctionalExcludedFromComposition() {
        UseCase<Factors, Integer, Boolean> fastButBroken = new UseCase<>() {
            @Override public void postconditions(ContractBuilder<Boolean> b) { /* none */ }
            @Override public Outcome<Boolean> invoke(Integer input, TokenTracker tracker) {
                sleep(5);
                return Outcome.fail("scripted", "functional fail intentional");
            }
        };
        Sampling<Factors, Integer, Boolean> sampling = Sampling
                .<Factors, Integer, Boolean>builder()
                .useCaseFactory(f -> fastButBroken)
                .inputs(1)
                .samples(5)
                .build();
        ProbabilisticTest spec = ProbabilisticTest
                .testing(sampling, new Factors("m"))
                .criterion(PercentileLatency.<Boolean>meeting(
                        LatencySpec.builder().p50Millis(100L).build(),
                        ThresholdOrigin.SLA))
                .reportOnly(BernoulliPassRate.<Boolean>meeting(0.95, ThresholdOrigin.SLA))
                .build();

        var r = (ProbabilisticTestResult) new Engine().run(spec);
        // Functional is a total FAIL but report-only; latency PASSes (5ms ≤ 100ms).
        assertThat(r.verdict()).isEqualTo(Verdict.PASS);
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }
}
