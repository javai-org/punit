package org.javai.punit.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.javai.punit.api.ThresholdOrigin;
import org.javai.punit.api.typed.DataGeneration;
import org.javai.punit.api.typed.LatencySpec;
import org.javai.punit.api.typed.Pacing;
import org.javai.punit.api.typed.SamplingShape;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.javai.punit.api.typed.spec.BudgetExhaustionPolicy;
import org.javai.punit.api.typed.spec.VerdictDimension;
import org.javai.punit.api.typed.spec.EngineResult;
import org.javai.punit.api.typed.spec.ExceptionPolicy;
import org.javai.punit.api.typed.spec.MeasureSpec;
import org.javai.punit.api.typed.spec.ProbabilisticTestSpec;
import org.javai.punit.api.typed.spec.ProbabilisticTestResult;
import org.javai.punit.api.typed.spec.SampleSummary;
import org.javai.punit.api.typed.spec.TerminationReason;
import org.javai.punit.api.typed.spec.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage-3 end-to-end engine behaviour: budget enforcement, pacing,
 * exception policy, latency computation, two-dimensional verdicts.
 *
 * <p>No JUnit extensions, no annotation scanning — these tests
 * construct typed specs directly and drive them through
 * {@link Engine}.
 */
@DisplayName("Engine Stage-3 resource controls + latency integration")
class EngineResourceControlsAndLatencyIntegrationTest {

    record Factors(String model) {}

    /** Deterministic duration-scripted use case. */
    private static final class ScriptedLatencyUseCase implements UseCase<Factors, Integer, Integer> {
        private final long[] scriptMillis;
        private int index = 0;

        ScriptedLatencyUseCase(long... scriptMillis) {
            this.scriptMillis = scriptMillis;
        }

        @Override public UseCaseOutcome<Integer> apply(Integer input) {
            long millis = scriptMillis[index % scriptMillis.length];
            index++;
            sleep(millis);
            return UseCaseOutcome.ok(input);
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

        @Override public UseCaseOutcome<Integer> apply(Integer input) {
            sleep(sleepMillis);
            return UseCaseOutcome.<Integer>ok(input).withTokens(tokens);
        }
    }

    // ── 1. RC01 time budget stops sampling early ─────────────────────

    @Test
    @DisplayName("RC01: time budget terminates sampling early with TIME_BUDGET marker")
    void timeBudgetTerminatesEarly() {
        UseCase<Factors, Integer, Integer> sleeper = new SleepyUseCase(100, 0);
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> sleeper)
                .inputs(1, 2, 3)
                .samples(1000)
                .timeBudget(Duration.ofMillis(500))
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        new Engine().run(spec);
        SampleSummary<Integer> summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isLessThanOrEqualTo(7);
        assertThat(summary.terminationReason()).isEqualTo(TerminationReason.TIME_BUDGET);
    }

    // ── 2. RC02 token budget stops sampling early ────────────────────

    @Test
    @DisplayName("RC02: token budget terminates sampling early with TOKEN_BUDGET marker")
    void tokenBudgetTerminatesEarly() {
        // Declaring a static per-sample charge of 100 matches the
        // BudgetTracker's pre-sample projection: after 2 samples the
        // running total is 200, and projected 200 + 100 > 250 aborts
        // the 3rd before it runs. A use case that reports 100 tokens
        // per outcome would be accounted post-sample; the *projection*
        // is static, so this scenario uses tokenCharge to model it.
        UseCase<Factors, Integer, Integer> zeroOutcomeTokens = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(Integer input) {
                return UseCaseOutcome.ok(input);
            }
        };
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> zeroOutcomeTokens)
                .inputs(1)
                .samples(100)
                .tokenBudget(250)
                .tokenCharge(100)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        new Engine().run(spec);
        SampleSummary<Integer> summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.tokensConsumed()).isEqualTo(200L);
        assertThat(summary.terminationReason()).isEqualTo(TerminationReason.TOKEN_BUDGET);
    }

    // ── 3. RC03 static charge contributes to budget ─────────────────

    @Test
    @DisplayName("RC03: static token charge is accounted for each sample")
    void staticChargeAccountedEachSample() {
        UseCase<Factors, Integer, Integer> zeroCost = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(Integer input) {
                return UseCaseOutcome.ok(input);
            }
        };
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> zeroCost)
                .inputs(1)
                .samples(5)
                .tokenCharge(50)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        new Engine().run(spec);
        SampleSummary<Integer> summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(5);
        assertThat(summary.tokensConsumed()).isEqualTo(50L * 5);
    }

    // ── 4. RC05 PASS_INCOMPLETE surfaces partial result ─────────────

    @Test
    @DisplayName("RC05: budget exhaustion produces a summary the spec can detect (terminatedEarly)")
    void passIncompleteSurfacesPartialResult() {
        UseCase<Factors, Integer, Integer> sleeper = new SleepyUseCase(50, 0);
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> sleeper)
                .inputs(1)
                .samples(100)
                .timeBudget(Duration.ofMillis(200))
                .onBudgetExhausted(BudgetExhaustionPolicy.PASS_INCOMPLETE)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        new Engine().run(spec);
        SampleSummary<Integer> summary = spec.lastSummary().orElseThrow();

        assertThat(summary.terminatedEarly()).isTrue();
        assertThat(summary.total()).isBetween(2, 7);
    }

    // ── 5 & 6. RC08 / RC10 pacing inserts inter-sample delay ────────

    @Test
    @DisplayName("RC08: maxRequestsPerSecond inserts inter-sample delay")
    void maxRpsInsertsDelay() {
        // 10 RPS → 100ms min delay between samples.
        UseCase<Factors, Integer, Integer> pacingUc = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(Integer input) {
                return UseCaseOutcome.ok(input);
            }
            @Override public Pacing pacing() {
                return Pacing.builder().maxRequestsPerSecond(10.0).build();
            }
        };
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> pacingUc)
                .inputs(1)
                .samples(5)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        long t0 = System.nanoTime();
        new Engine().run(spec);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        // 5 samples, 4 inter-sample gaps at ≥100ms each → ≥ ~400ms.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(380);
    }

    @Test
    @DisplayName("RC10: minMillisPerSample composes with RC08 via most-restrictive-wins")
    void mostRestrictiveWinsComposition() {
        // maxRps implies 100ms; min explicit 250ms should dominate.
        UseCase<Factors, Integer, Integer> pacingUc = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(Integer input) {
                return UseCaseOutcome.ok(input);
            }
            @Override public Pacing pacing() {
                return Pacing.builder()
                        .maxRequestsPerSecond(10.0)
                        .minMillisPerSample(250L)
                        .build();
            }
        };
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> pacingUc)
                .inputs(1)
                .samples(3)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        long t0 = System.nanoTime();
        new Engine().run(spec);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        // 3 samples, 2 gaps at ≥250ms each → ≥ ~500ms.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(480);
    }

    // ── 7 & 8. Exception policy paths ───────────────────────────────

    @Test
    @DisplayName("RC12: FAIL_SAMPLE catches a thrown defect and counts it as a failed sample")
    void failSampleCatchesAndCounts() {
        AtomicInteger n = new AtomicInteger();
        UseCase<Factors, Integer, Integer> flaky = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(Integer input) {
                int i = n.incrementAndGet();
                if (i % 2 == 0) {
                    throw new IllegalStateException("scripted defect " + i);
                }
                return UseCaseOutcome.ok(input);
            }
        };
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> flaky)
                .inputs(1)
                .samples(10)
                .onException(ExceptionPolicy.FAIL_SAMPLE)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        new Engine().run(spec);
        SampleSummary<Integer> summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(10);
        assertThat(summary.successes()).isEqualTo(5);
        assertThat(summary.failures()).isEqualTo(5);
    }

    @Test
    @DisplayName("RC13: ABORT_TEST (default) rethrows a thrown defect — preserves legacy behaviour")
    void abortTestRethrows() {
        UseCase<Factors, Integer, Integer> defective = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(Integer input) {
                throw new IllegalStateException("never mind the exception policy, this is a defect");
            }
        };
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> defective)
                .inputs(1)
                .samples(5)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        assertThatThrownBy(() -> new Engine().run(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("defect");
    }

    // ── 9. RC14 maxExampleFailures ──────────────────────────────────

    @Test
    @DisplayName("RC14: maxExampleFailures caps retained detail but not failure counts")
    void maxExampleFailuresCaps() {
        UseCase<Factors, Integer, Integer> failing = new UseCase<>() {
            @Override public UseCaseOutcome<Integer> apply(Integer input) {
                return UseCaseOutcome.fail("scripted", "boom");
            }
        };
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> failing)
                .inputs(1)
                .samples(50)
                .maxExampleFailures(3)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        new Engine().run(spec);
        SampleSummary<Integer> summary = spec.lastSummary().orElseThrow();

        assertThat(summary.total()).isEqualTo(50);
        assertThat(summary.failures()).isEqualTo(50);
        assertThat(summary.failuresDropped()).isEqualTo(47);
        // 3 failures retained in outcomes (no successes to swell the list)
        assertThat(summary.outcomes()).hasSize(3);
    }

    // ── 10. LT01 latency percentile computation ─────────────────────

    @Test
    @DisplayName("LT01: latency percentiles computed from observed durations (nearest-rank)")
    void latencyPercentilesComputed() {
        // Scripted sleeps 10/20/30/40/50ms.
        UseCase<Factors, Integer, Integer> scripted = new ScriptedLatencyUseCase(10L, 20L, 30L, 40L, 50L);
        DataGeneration<Factors, Integer, Integer> plan = SamplingShape
                .<Factors, Integer, Integer>builder()
                .useCaseFactory(f -> scripted)
                .inputs(1)
                .samples(5)
                .build()
                .at(new Factors("m"));
        MeasureSpec<Factors, Integer, Integer> spec = MeasureSpec.measuring(plan).build();

        new Engine().run(spec);
        SampleSummary<Integer> summary = spec.lastSummary().orElseThrow();
        var lr = summary.latencyResult();
        // Nearest-rank: p50 index = ceil(0.5*5)-1 = 2 -> 30ms; p90 = ceil(4.5)-1 = 4 -> 50ms.
        assertThat(lr.sampleCount()).isEqualTo(5);
        assertThat(lr.p50()).isGreaterThanOrEqualTo(Duration.ofMillis(28));
        assertThat(lr.p50()).isLessThanOrEqualTo(Duration.ofMillis(60));
        assertThat(lr.p90()).isGreaterThanOrEqualTo(Duration.ofMillis(48));
    }

    // ── 11. LT02 explicit threshold breach → FAIL latency verdict ───

    @Test
    @DisplayName("LT02: explicit threshold breach produces a FAIL latency verdict with a PercentileBreach")
    void latencyThresholdBreachProducesFailVerdict() {
        // 50ms sleeps, assert p50 ≤ 10ms.
        UseCase<Factors, Integer, Boolean> slow = new UseCase<>() {
            @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                sleep(50);
                return UseCaseOutcome.ok(Boolean.TRUE);
            }
        };
        ProbabilisticTestSpec<Factors, Integer, Boolean> spec = ProbabilisticTestSpec
                .<Factors, Integer, Boolean>normative()
                .useCaseFactory(f -> slow)
                .factors(new Factors("m"))
                .inputs(1)
                .samples(5)
                .threshold(0.95, ThresholdOrigin.SLA)
                .latency(LatencySpec.builder().p50Millis(10L).build())
                .build();

        EngineResult outcome = new Engine().run(spec);
        ProbabilisticTestResult v =
                ((ProbabilisticTestResult) outcome);

        assertThat(v.latencyVerdict()).isPresent();
        assertThat(v.latencyVerdict().get().verdict()).isEqualTo(Verdict.FAIL);
        assertThat(v.latencyVerdict().get().breaches()).hasSize(1);
        assertThat(v.latencyVerdict().get().breaches().get(0).percentile()).isEqualTo("p50");
    }

    // ── 12. LT05 two-dimensional verdict: functional pass + latency fail

    @Test
    @DisplayName("LT05: functional PASS + latency FAIL under BOTH yields FAIL; under FUNCTIONAL yields PASS")
    void twoVerdictDimensionalVerdictProjection() {
        UseCase<Factors, Integer, Boolean> slow = new UseCase<>() {
            @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                sleep(50);
                return UseCaseOutcome.ok(Boolean.TRUE);
            }
        };

        // Default assertOn when latency is declared is BOTH.
        ProbabilisticTestSpec<Factors, Integer, Boolean> both = ProbabilisticTestSpec
                .<Factors, Integer, Boolean>normative()
                .useCaseFactory(f -> slow)
                .factors(new Factors("m"))
                .inputs(1)
                .samples(5)
                .threshold(0.95, ThresholdOrigin.SLA)
                .latency(LatencySpec.builder().p50Millis(10L).build())
                .build();
        var vBoth = ((ProbabilisticTestResult) new Engine().run(both));
        assertThat(vBoth.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(vBoth.latencyVerdict()).isPresent();
        assertThat(vBoth.latencyVerdict().get().verdict()).isEqualTo(Verdict.FAIL);

        // Project only the functional side.
        ProbabilisticTestSpec<Factors, Integer, Boolean> funcOnly = ProbabilisticTestSpec
                .<Factors, Integer, Boolean>normative()
                .useCaseFactory(f -> slow)
                .factors(new Factors("m"))
                .inputs(1)
                .samples(5)
                .threshold(0.95, ThresholdOrigin.SLA)
                .latency(LatencySpec.builder().p50Millis(10L).build())
                .assertOn(VerdictDimension.FUNCTIONAL)
                .build();
        var vFunc = ((ProbabilisticTestResult) new Engine().run(funcOnly));
        assertThat(vFunc.verdict()).isEqualTo(Verdict.PASS);
        // Latency verdict side channel still populated (it was declared).
        assertThat(vFunc.latencyVerdict()).isPresent();
        assertThat(vFunc.latencyVerdict().get().verdict()).isEqualTo(Verdict.FAIL);
    }

    // ── 13. LT06 assertOn(LATENCY) ──────────────────────────────────

    @Test
    @DisplayName("LT06: assertOn(LATENCY) projects the latency-only verdict")
    void assertOnLatencyOnly() {
        UseCase<Factors, Integer, Boolean> fastButBroken = new UseCase<>() {
            @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                sleep(5);
                return UseCaseOutcome.fail("scripted", "functional fail intentional");
            }
        };

        ProbabilisticTestSpec<Factors, Integer, Boolean> spec = ProbabilisticTestSpec
                .<Factors, Integer, Boolean>normative()
                .useCaseFactory(f -> fastButBroken)
                .factors(new Factors("m"))
                .inputs(1)
                .samples(5)
                .threshold(0.95, ThresholdOrigin.SLA)
                .latency(LatencySpec.builder().p50Millis(100L).build())
                .assertOn(VerdictDimension.LATENCY)
                .build();

        var v = ((ProbabilisticTestResult) new Engine().run(spec));

        // Functional side is a total FAIL (contract violations) but
        // assertOn(LATENCY) ignores it — latency passes (5ms ≤ 100ms).
        assertThat(v.verdict()).isEqualTo(Verdict.PASS);
        assertThat(v.latencyVerdict()).isPresent();
        assertThat(v.latencyVerdict().get().verdict()).isEqualTo(Verdict.PASS);
    }

    // ── verdict round-trip (acceptance criterion 6) ─────────────────

    @Test
    @DisplayName("ProbabilisticTestResult round-trips a two-dimensional verdict")
    void verdictRoundTripsTwoVerdictDimensionalShape() {
        UseCase<Factors, Integer, Boolean> fine = new UseCase<>() {
            @Override public UseCaseOutcome<Boolean> apply(Integer input) {
                return UseCaseOutcome.ok(Boolean.TRUE);
            }
        };
        ProbabilisticTestSpec<Factors, Integer, Boolean> spec = ProbabilisticTestSpec
                .<Factors, Integer, Boolean>normative()
                .useCaseFactory(f -> fine)
                .factors(new Factors("m"))
                .inputs(1)
                .samples(10)
                .threshold(0.95, ThresholdOrigin.SLO)
                .latency(LatencySpec.builder().p95Millis(500L).build())
                .build();

        var v = ((ProbabilisticTestResult) new Engine().run(spec));
        assertThat(v.verdict()).isEqualTo(Verdict.PASS);
        assertThat(v.latencyVerdict()).isPresent();
        assertThat(v.latencyVerdict().get().verdict()).isEqualTo(Verdict.PASS);
        assertThat(v.latencyVerdict().get().breaches()).isEmpty();
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
