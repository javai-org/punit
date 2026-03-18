package org.javai.punit.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@DisplayName("VerdictXmlWriter")
class VerdictXmlWriterTest {

    private final VerdictXmlWriter writer = new VerdictXmlWriter();

    @Nested
    @DisplayName("minimal verdict")
    class MinimalVerdict {

        @Test
        @DisplayName("serialises a minimal passing verdict to valid XML")
        void minimialPassingVerdict() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element root = doc.getDocumentElement();

            assertThat(root.getLocalName()).isEqualTo("punit-verdict");
            assertThat(root.getAttribute("version")).isEqualTo("1.0");
            assertThat(root.getAttribute("correlation-id")).isEqualTo("v:test01");
        }

        @Test
        @DisplayName("includes identity element with class and method names")
        void includesIdentity() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element identity = firstElement(doc, "identity");

            assertThat(identity.getAttribute("class-name")).isEqualTo("com.example.MyTest");
            assertThat(identity.getAttribute("method-name")).isEqualTo("shouldPass");
        }

        @Test
        @DisplayName("includes execution summary attributes")
        void includesExecution() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element exec = firstElement(doc, "execution");

            assertThat(exec.getAttribute("planned-samples")).isEqualTo("100");
            assertThat(exec.getAttribute("successes")).isEqualTo("95");
            assertThat(exec.getAttribute("intent")).isEqualTo("VERIFICATION");
        }

        @Test
        @DisplayName("includes verdict element")
        void includesVerdictElement() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element v = firstElement(doc, "verdict");

            assertThat(v.getAttribute("junit-passed")).isEqualTo("true");
            assertThat(v.getAttribute("punit-verdict")).isEqualTo("PASS");
        }

        @Test
        @DisplayName("omits functional element when absent")
        void omitsFunctionalWhenAbsent() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "functional");

            assertThat(nodes.getLength()).isZero();
        }

        @Test
        @DisplayName("omits latency element when absent")
        void omitsLatencyWhenAbsent() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "latency");

            assertThat(nodes.getLength()).isZero();
        }
    }

    @Nested
    @DisplayName("functional dimension")
    class FunctionalDimensionTests {

        @Test
        @DisplayName("serialises functional dimension when present")
        void serialisesFunctionalDimension() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithFunctional();

            Document doc = writeAndParse(verdict);
            Element func = firstElement(doc, "functional");

            assertThat(func.getAttribute("successes")).isEqualTo("95");
            assertThat(func.getAttribute("failures")).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("latency dimension")
    class LatencyDimensionTests {

        @Test
        @DisplayName("serialises latency with distribution and assertions")
        void serialisesLatency() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithLatency();

            Document doc = writeAndParse(verdict);
            Element lat = firstElement(doc, "latency");

            assertThat(lat.getAttribute("successful-samples")).isEqualTo("90");
            assertThat(lat.getAttribute("skipped")).isEqualTo("false");

            Element dist = firstElement(doc, "distribution");
            assertThat(dist.getAttribute("p95")).isEqualTo("420");

            NodeList percentiles = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "percentile");
            assertThat(percentiles.getLength()).isEqualTo(1);
            Element p95 = (Element) percentiles.item(0);
            assertThat(p95.getAttribute("label")).isEqualTo("p95");
            assertThat(p95.getAttribute("passed")).isEqualTo("true");
        }

        @Test
        @DisplayName("omits distribution when latency is skipped")
        void omitsDistributionWhenSkipped() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithSkippedLatency();

            Document doc = writeAndParse(verdict);
            Element lat = firstElement(doc, "latency");

            assertThat(lat.getAttribute("skipped")).isEqualTo("true");
            assertThat(lat.getAttribute("skip-reason")).isEqualTo("No successes");

            NodeList dists = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "distribution");
            assertThat(dists.getLength()).isZero();
        }

        @Test
        @DisplayName("serialises latency caveats")
        void serialisesCaveats() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithLatency();

            Document doc = writeAndParse(verdict);
            NodeList caveats = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "caveat");

            assertThat(caveats.getLength()).isEqualTo(1);
            assertThat(caveats.item(0).getTextContent()).isEqualTo("Small sample");
        }
    }

    @Nested
    @DisplayName("statistics")
    class StatisticsTests {

        @Test
        @DisplayName("serialises statistical analysis attributes")
        void serialisesStatistics() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element stats = firstElement(doc, "statistics");

            assertThat(stats.getAttribute("confidence-level")).isEqualTo("0.95");
            assertThat(stats.getAttribute("standard-error")).isNotEmpty();
        }

        @Test
        @DisplayName("serialises baseline when present")
        void serialisesBaseline() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithBaseline();

            Document doc = writeAndParse(verdict);
            Element baseline = firstElement(doc, "baseline");

            assertThat(baseline.getAttribute("source-file")).isEqualTo("my-spec.yaml");
            assertThat(baseline.getAttribute("samples")).isEqualTo("1000");
        }
    }

    @Nested
    @DisplayName("covariates")
    class CovariateTests {

        @Test
        @DisplayName("serialises aligned covariates")
        void serialisesAligned() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element cov = firstElement(doc, "covariates");

            assertThat(cov.getAttribute("aligned")).isEqualTo("true");
        }

        @Test
        @DisplayName("serialises misaligned covariates")
        void serialisesMisaligned() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithMisalignment();

            Document doc = writeAndParse(verdict);
            Element cov = firstElement(doc, "covariates");

            assertThat(cov.getAttribute("aligned")).isEqualTo("false");

            NodeList misalignments = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "misalignment");
            assertThat(misalignments.getLength()).isEqualTo(1);
            Element m = (Element) misalignments.item(0);
            assertThat(m.getAttribute("key")).isEqualTo("model");
            assertThat(m.getAttribute("baseline-value")).isEqualTo("gpt-4");
            assertThat(m.getAttribute("test-value")).isEqualTo("gpt-4o");
        }
    }

    @Nested
    @DisplayName("provenance and expiration")
    class ProvenanceTests {

        @Test
        @DisplayName("serialises provenance with expiration")
        void serialisesProvenance() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithProvenance();

            Document doc = writeAndParse(verdict);
            Element prov = firstElement(doc, "provenance");

            assertThat(prov.getAttribute("origin")).isEqualTo("SLA");
            assertThat(prov.getAttribute("contract-ref")).isEqualTo("SLA-PAY-001");

            Element exp = firstElement(doc, "expiration");
            assertThat(exp.getAttribute("status")).isEqualTo("EXPIRING_SOON");
            assertThat(exp.getAttribute("requires-warning")).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("environment metadata")
    class EnvironmentTests {

        @Test
        @DisplayName("serialises environment metadata entries")
        void serialisesEnvironment() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithEnvironment();

            Document doc = writeAndParse(verdict);
            NodeList entries = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "entry");

            assertThat(entries.getLength()).isEqualTo(2);
        }

        @Test
        @DisplayName("omits environment element when metadata is empty")
        void omitsWhenEmpty() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "environment");

            assertThat(nodes.getLength()).isZero();
        }
    }

    @Nested
    @DisplayName("termination")
    class TerminationTests {

        @Test
        @DisplayName("serialises completed termination")
        void serialisesCompleted() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element term = firstElement(doc, "termination");

            assertThat(term.getAttribute("reason")).isEqualTo("COMPLETED");
            assertThat(term.hasAttribute("details")).isFalse();
        }

        @Test
        @DisplayName("serialises budget exhaustion with details")
        void serialisesBudgetExhaustion() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithBudgetExhaustion();

            Document doc = writeAndParse(verdict);
            Element term = firstElement(doc, "termination");

            assertThat(term.getAttribute("reason")).isEqualTo("METHOD_TIME_BUDGET_EXHAUSTED");
            assertThat(term.getAttribute("details")).isEqualTo("Time budget exceeded");
        }
    }

    @Nested
    @DisplayName("schema validation")
    class SchemaValidation {

        @Test
        @DisplayName("minimal verdict validates against XSD")
        void minimalVerdictValidatesAgainstXsd() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            String xml = writeToString(verdict);

            validateAgainstSchema(xml);
        }

        @Test
        @DisplayName("full verdict validates against XSD")
        void fullVerdictValidatesAgainstXsd() throws Exception {
            ProbabilisticTestVerdict verdict = fullVerdict();

            String xml = writeToString(verdict);

            validateAgainstSchema(xml);
        }
    }

    @Nested
    @DisplayName("use case ID")
    class UseCaseIdTests {

        @Test
        @DisplayName("includes use-case-id when present")
        void includesUseCaseId() throws Exception {
            ProbabilisticTestVerdict verdict = verdictWithUseCaseId();

            Document doc = writeAndParse(verdict);
            Element identity = firstElement(doc, "identity");

            assertThat(identity.getAttribute("use-case-id")).isEqualTo("payment-gateway");
        }

        @Test
        @DisplayName("omits use-case-id when absent")
        void omitsUseCaseId() throws Exception {
            ProbabilisticTestVerdict verdict = minimalVerdict(true, PunitVerdict.PASS);

            Document doc = writeAndParse(verdict);
            Element identity = firstElement(doc, "identity");

            assertThat(identity.hasAttribute("use-case-id")).isFalse();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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

    private Document writeAndParse(ProbabilisticTestVerdict verdict) throws Exception {
        String xml = writeToString(verdict);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String writeToString(ProbabilisticTestVerdict verdict) throws XMLStreamException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(verdict, baos);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private Element firstElement(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, localName);
        assertThat(nodes.getLength()).as("Expected element <%s> to exist", localName).isGreaterThan(0);
        return (Element) nodes.item(0);
    }

    private void validateAgainstSchema(String xml) throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try (InputStream xsdStream = getClass().getResourceAsStream("/org/javai/punit/report/punit-verdict.xsd")) {
            assertThat(xsdStream).as("XSD resource must be available").isNotNull();
            Schema schema = schemaFactory.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
