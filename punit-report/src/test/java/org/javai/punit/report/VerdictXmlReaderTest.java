package org.javai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.*;
import org.javai.punit.verdict.PunitVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VerdictXmlReader")
class VerdictXmlReaderTest {

    private final VerdictXmlWriter writer = new VerdictXmlWriter();
    private final VerdictXmlReader reader = new VerdictXmlReader();

    @Nested
    @DisplayName("round-trip: minimal verdict")
    class MinimalRoundTrip {

        @Test
        @DisplayName("preserves correlation ID and timestamp")
        void preservesCorrelationIdAndTimestamp() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PunitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.correlationId()).isEqualTo("v:test01");
            assertThat(result.timestamp()).isEqualTo(Instant.parse("2026-03-11T14:30:00Z"));
        }

        @Test
        @DisplayName("preserves identity")
        void preservesIdentity() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PunitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.identity().className()).isEqualTo("com.example.MyTest");
            assertThat(result.identity().methodName()).isEqualTo("shouldPass");
            assertThat(result.identity().useCaseId()).isEmpty();
        }

        @Test
        @DisplayName("preserves execution summary")
        void preservesExecution() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PunitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            ExecutionSummary exec = result.execution();
            assertThat(exec.plannedSamples()).isEqualTo(100);
            assertThat(exec.samplesExecuted()).isEqualTo(100);
            assertThat(exec.successes()).isEqualTo(95);
            assertThat(exec.failures()).isEqualTo(5);
            assertThat(exec.minPassRate()).isEqualTo(0.9);
            assertThat(exec.observedPassRate()).isEqualTo(0.95);
            assertThat(exec.elapsedMs()).isEqualTo(150);
            assertThat(exec.appliedMultiplier()).isEmpty();
            assertThat(exec.intent()).isEqualTo(TestIntent.VERIFICATION);
            assertThat(exec.resolvedConfidence()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("preserves verdict")
        void preservesVerdict() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PunitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.junitPassed()).isTrue();
            assertThat(result.punitVerdict()).isEqualTo(PunitVerdict.PASS);
        }

        @Test
        @DisplayName("preserves statistics")
        void preservesStatistics() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PunitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            StatisticalAnalysis stats = result.statistics();
            assertThat(stats.confidenceLevel()).isEqualTo(0.95);
            assertThat(stats.standardError()).isEqualTo(0.0218);
            assertThat(stats.testStatistic()).isPresent();
            assertThat(stats.pValue()).isPresent();
        }

        @Test
        @DisplayName("preserves empty optionals")
        void preservesEmptyOptionals() throws Exception {
            ProbabilisticTestVerdict original = minimalVerdict(true, PunitVerdict.PASS);

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.functional()).isEmpty();
            assertThat(result.latency()).isEmpty();
            assertThat(result.pacing()).isEmpty();
            assertThat(result.provenance()).isEmpty();
        }
    }

    @Nested
    @DisplayName("round-trip: functional dimension")
    class FunctionalRoundTrip {

        @Test
        @DisplayName("preserves functional dimension")
        void preservesFunctional() throws Exception {
            ProbabilisticTestVerdict original = verdictWithFunctional();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.functional()).isPresent();
            FunctionalDimension func = result.functional().get();
            assertThat(func.successes()).isEqualTo(95);
            assertThat(func.failures()).isEqualTo(5);
            assertThat(func.passRate()).isEqualTo(0.95);
        }
    }

    @Nested
    @DisplayName("round-trip: latency dimension")
    class LatencyRoundTrip {

        @Test
        @DisplayName("preserves latency with distribution and assertions")
        void preservesLatency() throws Exception {
            ProbabilisticTestVerdict original = verdictWithLatency();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.latency()).isPresent();
            LatencyDimension lat = result.latency().get();
            assertThat(lat.successfulSamples()).isEqualTo(90);
            assertThat(lat.totalSamples()).isEqualTo(100);
            assertThat(lat.skipped()).isFalse();
            assertThat(lat.p95Ms()).isEqualTo(420);
            assertThat(lat.assertions()).hasSize(1);
            assertThat(lat.assertions().get(0).label()).isEqualTo("p95");
            assertThat(lat.assertions().get(0).passed()).isTrue();
            assertThat(lat.assertions().get(0).source()).isEqualTo("from baseline");
            assertThat(lat.caveats()).containsExactly("Small sample");
        }

        @Test
        @DisplayName("preserves skipped latency")
        void preservesSkippedLatency() throws Exception {
            ProbabilisticTestVerdict original = verdictWithSkippedLatency();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.latency()).isPresent();
            LatencyDimension lat = result.latency().get();
            assertThat(lat.skipped()).isTrue();
            assertThat(lat.skipReason()).contains("No successes");
        }
    }

    @Nested
    @DisplayName("round-trip: baseline and covariates")
    class BaselineAndCovariateRoundTrip {

        @Test
        @DisplayName("preserves baseline")
        void preservesBaseline() throws Exception {
            ProbabilisticTestVerdict original = verdictWithBaseline();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.statistics().baseline()).isPresent();
            BaselineSummary b = result.statistics().baseline().get();
            assertThat(b.sourceFile()).isEqualTo("my-spec.yaml");
            assertThat(b.baselineSamples()).isEqualTo(1000);
            assertThat(b.baselineSuccesses()).isEqualTo(940);
            assertThat(b.baselineRate()).isEqualTo(0.94);
            assertThat(b.derivedThreshold()).isEqualTo(0.92);
        }

        @Test
        @DisplayName("preserves misaligned covariates")
        void preservesMisalignment() throws Exception {
            ProbabilisticTestVerdict original = verdictWithMisalignment();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.covariates().aligned()).isFalse();
            assertThat(result.covariates().misalignments()).hasSize(1);
            Misalignment m = result.covariates().misalignments().get(0);
            assertThat(m.covariateKey()).isEqualTo("model");
            assertThat(m.baselineValue()).isEqualTo("gpt-4");
            assertThat(m.testValue()).isEqualTo("gpt-4o");
        }
    }

    @Nested
    @DisplayName("round-trip: provenance")
    class ProvenanceRoundTrip {

        @Test
        @DisplayName("preserves provenance with expiration")
        void preservesProvenance() throws Exception {
            ProbabilisticTestVerdict original = verdictWithProvenance();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.provenance()).isPresent();
            SpecProvenance prov = result.provenance().get();
            assertThat(prov.thresholdOriginName()).isEqualTo("SLA");
            assertThat(prov.contractRef()).isEqualTo("SLA-PAY-001");

            assertThat(prov.expiration()).isPresent();
            ExpirationInfo exp = prov.expiration().get();
            assertThat(exp.status().requiresWarning()).isTrue();
            assertThat(exp.expiresAt()).contains(Instant.parse("2026-04-01T00:00:00Z"));
        }
    }

    @Nested
    @DisplayName("round-trip: environment and termination")
    class EnvironmentAndTerminationRoundTrip {

        @Test
        @DisplayName("preserves environment metadata")
        void preservesEnvironment() throws Exception {
            ProbabilisticTestVerdict original = verdictWithEnvironment();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.environmentMetadata()).hasSize(2);
            assertThat(result.environmentMetadata()).containsEntry("environment", "staging");
            assertThat(result.environmentMetadata()).containsEntry("instance", "web-01");
        }

        @Test
        @DisplayName("preserves budget exhaustion termination")
        void preservesBudgetExhaustion() throws Exception {
            ProbabilisticTestVerdict original = verdictWithBudgetExhaustion();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.termination().reason()).isEqualTo(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED);
            assertThat(result.termination().details()).contains("Time budget exceeded");
        }
    }

    @Nested
    @DisplayName("round-trip: use case ID")
    class UseCaseIdRoundTrip {

        @Test
        @DisplayName("preserves use case ID when present")
        void preservesUseCaseId() throws Exception {
            ProbabilisticTestVerdict original = verdictWithUseCaseId();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.identity().useCaseId()).contains("payment-gateway");
        }
    }

    @Nested
    @DisplayName("round-trip: full verdict")
    class FullRoundTrip {

        @Test
        @DisplayName("round-trips a full verdict with all optional fields")
        void roundTripsFullVerdict() throws Exception {
            ProbabilisticTestVerdict original = fullVerdict();

            ProbabilisticTestVerdict result = roundTrip(original);

            assertThat(result.correlationId()).isEqualTo(original.correlationId());
            assertThat(result.identity().useCaseId()).contains("payment-gateway");
            assertThat(result.functional()).isPresent();
            assertThat(result.latency()).isPresent();
            assertThat(result.pacing()).isPresent();
            assertThat(result.provenance()).isPresent();
            assertThat(result.statistics().baseline()).isPresent();
            assertThat(result.environmentMetadata()).containsEntry("environment", "staging");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ProbabilisticTestVerdict roundTrip(ProbabilisticTestVerdict verdict) throws XMLStreamException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(verdict, baos);
        return reader.read(new ByteArrayInputStream(baos.toByteArray()));
    }

    private ProbabilisticTestVerdict minimalVerdict(boolean passed, PunitVerdict punitVerdict) {
        return new ProbabilisticTestVerdict(
                "v:test01",
                Instant.parse("2026-03-11T14:30:00Z"),
                new TestIdentity("com.example.MyTest", "shouldPass", Optional.empty()),
                new ExecutionSummary(100, 100, 95, 5, 0.9, 0.95, 150,
                        Optional.empty(), TestIntent.VERIFICATION, 0.95, UseCaseAttributes.DEFAULT),
                Optional.empty(),
                Optional.empty(),
                new StatisticalAnalysis(0.95, 0.0218, 0.8948, 0.9798,
                        Optional.of(2.29), Optional.of(0.011),
                        Optional.empty(), Optional.empty(), List.of()),
                CovariateStatus.allAligned(),
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of(),
                passed,
                punitVerdict
        );
    }

    private ProbabilisticTestVerdict verdictWithFunctional() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PunitVerdict.PASS);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                base.latency(), base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithLatency() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PunitVerdict.PASS);
        LatencyDimension latency = new LatencyDimension(
                90, 100, false, Optional.empty(),
                120, 340, 420, 810, 1250,
                List.of(new PercentileAssertion("p95", 420, 500, true, false, "from baseline")),
                List.of("Small sample"),
                90, 10
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithSkippedLatency() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PunitVerdict.PASS);
        LatencyDimension latency = new LatencyDimension(
                0, 100, true, Optional.of("No successes"),
                0, 0, 0, 0, 0,
                List.of(), List.of(), 0, 0
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), Optional.of(latency),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithBaseline() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PunitVerdict.PASS);
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948, 0.9798,
                Optional.of(2.29), Optional.of(0.011),
                Optional.of("Wilson score lower bound"),
                Optional.of(new BaselineSummary(
                        "my-spec.yaml", Instant.parse("2026-02-15T00:00:00Z"),
                        1000, 940, 0.94, 0.92)),
                List.of()
        );
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                stats, base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithMisalignment() {
        ProbabilisticTestVerdict base = minimalVerdict(false, PunitVerdict.INCONCLUSIVE);
        CovariateStatus cov = new CovariateStatus(false,
                List.of(new Misalignment("model", "gpt-4", "gpt-4o")));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), cov, base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithProvenance() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PunitVerdict.PASS);
        ExpirationStatus expiringStatus = ExpirationStatus.expiringSoon(
                java.time.Duration.ofDays(7), 0.20);
        SpecProvenance prov = new SpecProvenance("SLA", "SLA-PAY-001", "payment-gateway.yaml",
                Optional.of(new ExpirationInfo(expiringStatus,
                        Optional.of(Instant.parse("2026-04-01T00:00:00Z")))));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), Optional.of(prov), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithEnvironment() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PunitVerdict.PASS);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                Map.of("environment", "staging", "instance", "web-01"),
                base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict verdictWithBudgetExhaustion() {
        ProbabilisticTestVerdict base = minimalVerdict(false, PunitVerdict.FAIL);
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), base.identity(), base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(),
                new Termination(TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED,
                        Optional.of("Time budget exceeded")),
                base.environmentMetadata(), false, PunitVerdict.FAIL
        );
    }

    private ProbabilisticTestVerdict verdictWithUseCaseId() {
        ProbabilisticTestVerdict base = minimalVerdict(true, PunitVerdict.PASS);
        TestIdentity identity = new TestIdentity(
                "com.example.MyTest", "shouldPass", Optional.of("payment-gateway"));
        return new ProbabilisticTestVerdict(
                base.correlationId(), base.timestamp(), identity, base.execution(),
                base.functional(), base.latency(),
                base.statistics(), base.covariates(), base.cost(),
                base.pacing(), base.provenance(), base.termination(),
                base.environmentMetadata(), base.junitPassed(), base.punitVerdict()
        );
    }

    private ProbabilisticTestVerdict fullVerdict() {
        ProbabilisticTestVerdict base = verdictWithLatency();
        TestIdentity identity = new TestIdentity(
                "com.example.PaymentTest", "shouldMeetSla", Optional.of("payment-gateway"));
        ExpirationStatus expiringStatus = ExpirationStatus.valid(java.time.Duration.ofDays(30));
        SpecProvenance prov = new SpecProvenance("SLA", "SLA-PAY-001", "payment-gateway.yaml",
                Optional.of(new ExpirationInfo(expiringStatus, Optional.of(Instant.parse("2026-04-15T00:00:00Z")))));
        StatisticalAnalysis stats = new StatisticalAnalysis(
                0.95, 0.0218, 0.8948, 0.9798,
                Optional.of(2.29), Optional.of(0.011),
                Optional.of("Wilson score lower bound"),
                Optional.of(new BaselineSummary("payment-gateway.yaml",
                        Instant.parse("2026-02-15T00:00:00Z"), 1000, 940, 0.94, 0.92)),
                List.of("Covariate aligned")
        );
        PacingSummary pacing = new PacingSummary(10.0, 600.0, 36000.0, 4, 100, 4, 10.0);

        return new ProbabilisticTestVerdict(
                "v:full01", Instant.parse("2026-03-11T14:30:00Z"),
                identity, base.execution(),
                Optional.of(new FunctionalDimension(95, 5, 0.95)),
                base.latency(),
                stats, CovariateStatus.allAligned(),
                new CostSummary(500, 30000, 10000, TokenMode.DYNAMIC,
                        Optional.of(new BudgetSnapshot(60000, 15000, 50000, 2000)),
                        Optional.empty()),
                Optional.of(pacing), Optional.of(prov),
                new Termination(TerminationReason.COMPLETED, Optional.empty()),
                Map.of("environment", "staging"),
                true, PunitVerdict.PASS
        );
    }
}
