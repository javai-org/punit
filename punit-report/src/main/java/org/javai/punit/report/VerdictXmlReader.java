package org.javai.punit.report;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.javai.punit.api.TestIntent;
import org.javai.punit.controls.budget.CostBudgetMonitor.TokenMode;
import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.*;
import org.javai.punit.verdict.PunitVerdict;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Deserialises a {@link ProbabilisticTestVerdict} from XML produced by {@link VerdictXmlWriter}.
 *
 * <p>Uses DOM parsing since individual verdict files are small.
 */
public final class VerdictXmlReader {

    private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();

    static {
        FACTORY.setNamespaceAware(true);
    }

    /**
     * Reads a verdict from the given input stream.
     * The stream is not closed by this method.
     */
    public ProbabilisticTestVerdict read(InputStream in) {
        try {
            DocumentBuilder builder = FACTORY.newDocumentBuilder();
            Document doc = builder.parse(in);
            return readVerdict(doc.getDocumentElement());
        } catch (Exception e) {
            throw new XmlReadException("Failed to read verdict XML", e);
        }
    }

    private ProbabilisticTestVerdict readVerdict(Element root) {
        String correlationId = root.getAttribute("correlation-id");
        Instant timestamp = Instant.parse(root.getAttribute("timestamp"));

        TestIdentity identity = readIdentity(firstElement(root, "identity"));
        ExecutionSummary execution = readExecution(firstElement(root, "execution"));
        Optional<FunctionalDimension> functional = optionalElement(root, "functional")
                .map(this::readFunctional);
        Optional<LatencyDimension> latency = optionalElement(root, "latency")
                .map(this::readLatency);
        StatisticalAnalysis statistics = readStatistics(firstElement(root, "statistics"));
        CovariateStatus covariates = readCovariates(firstElement(root, "covariates"));
        CostSummary cost = readCost(firstElement(root, "cost"));
        Optional<PacingSummary> pacing = optionalElement(root, "pacing")
                .map(this::readPacing);
        Optional<SpecProvenance> provenance = optionalElement(root, "provenance")
                .map(this::readProvenance);
        Termination termination = readTermination(firstElement(root, "termination"));
        Map<String, String> environment = readEnvironment(root);
        Element verdictEl = firstElement(root, "verdict");
        boolean junitPassed = Boolean.parseBoolean(verdictEl.getAttribute("junit-passed"));
        PunitVerdict punitVerdict = PunitVerdict.valueOf(verdictEl.getAttribute("punit-verdict"));

        return new ProbabilisticTestVerdict(
                correlationId, timestamp, identity, execution,
                functional, latency, statistics, covariates, cost,
                pacing, provenance, termination, environment,
                junitPassed, punitVerdict
        );
    }

    private TestIdentity readIdentity(Element el) {
        String className = el.getAttribute("class-name");
        String methodName = el.getAttribute("method-name");
        Optional<String> useCaseId = optionalAttribute(el, "use-case-id");
        return new TestIdentity(className, methodName, useCaseId);
    }

    private ExecutionSummary readExecution(Element el) {
        return new ExecutionSummary(
                Integer.parseInt(el.getAttribute("planned-samples")),
                Integer.parseInt(el.getAttribute("samples-executed")),
                Integer.parseInt(el.getAttribute("successes")),
                Integer.parseInt(el.getAttribute("failures")),
                Double.parseDouble(el.getAttribute("min-pass-rate")),
                Double.parseDouble(el.getAttribute("observed-pass-rate")),
                Long.parseLong(el.getAttribute("elapsed-ms")),
                optionalAttribute(el, "applied-multiplier").map(Double::parseDouble),
                TestIntent.valueOf(el.getAttribute("intent")),
                Double.parseDouble(el.getAttribute("confidence"))
        );
    }

    private FunctionalDimension readFunctional(Element el) {
        return new FunctionalDimension(
                Integer.parseInt(el.getAttribute("successes")),
                Integer.parseInt(el.getAttribute("failures")),
                Double.parseDouble(el.getAttribute("pass-rate"))
        );
    }

    private LatencyDimension readLatency(Element el) {
        int successfulSamples = Integer.parseInt(el.getAttribute("successful-samples"));
        int totalSamples = Integer.parseInt(el.getAttribute("total-samples"));
        boolean skipped = Boolean.parseBoolean(el.getAttribute("skipped"));
        Optional<String> skipReason = optionalAttribute(el, "skip-reason");
        int dimensionSuccesses = Integer.parseInt(el.getAttribute("dimension-successes"));
        int dimensionFailures = Integer.parseInt(el.getAttribute("dimension-failures"));

        long p50 = 0, p90 = 0, p95 = 0, p99 = 0, max = 0;
        List<PercentileAssertion> assertions = List.of();
        List<String> caveats = List.of();

        if (!skipped) {
            Optional<Element> distEl = optionalElement(el, "distribution");
            if (distEl.isPresent()) {
                Element d = distEl.get();
                p50 = Long.parseLong(d.getAttribute("p50"));
                p90 = Long.parseLong(d.getAttribute("p90"));
                p95 = Long.parseLong(d.getAttribute("p95"));
                p99 = Long.parseLong(d.getAttribute("p99"));
                max = Long.parseLong(d.getAttribute("max"));
            }
            assertions = readAssertions(el);
        }
        caveats = readCaveats(el);

        return new LatencyDimension(
                successfulSamples, totalSamples, skipped, skipReason,
                p50, p90, p95, p99, max,
                assertions, caveats, dimensionSuccesses, dimensionFailures
        );
    }

    private List<PercentileAssertion> readAssertions(Element parent) {
        Optional<Element> assertionsEl = optionalElement(parent, "assertions");
        if (assertionsEl.isEmpty()) {
            return List.of();
        }
        List<PercentileAssertion> result = new ArrayList<>();
        NodeList percentiles = assertionsEl.get().getElementsByTagNameNS(
                VerdictXmlWriter.NAMESPACE, "percentile");
        for (int i = 0; i < percentiles.getLength(); i++) {
            Element p = (Element) percentiles.item(i);
            result.add(new PercentileAssertion(
                    p.getAttribute("label"),
                    Long.parseLong(p.getAttribute("observed")),
                    Long.parseLong(p.getAttribute("threshold")),
                    Boolean.parseBoolean(p.getAttribute("passed")),
                    Boolean.parseBoolean(p.getAttribute("indicative")),
                    p.hasAttribute("source") ? p.getAttribute("source") : null
            ));
        }
        return result;
    }

    private StatisticalAnalysis readStatistics(Element el) {
        Optional<BaselineSummary> baseline = optionalElement(el, "baseline")
                .map(this::readBaseline);
        List<String> caveats = readCaveats(el);

        return new StatisticalAnalysis(
                Double.parseDouble(el.getAttribute("confidence-level")),
                Double.parseDouble(el.getAttribute("standard-error")),
                Double.parseDouble(el.getAttribute("ci-lower")),
                Double.parseDouble(el.getAttribute("ci-upper")),
                optionalAttribute(el, "test-statistic").map(Double::parseDouble),
                optionalAttribute(el, "p-value").map(Double::parseDouble),
                optionalAttribute(el, "threshold-derivation"),
                baseline,
                caveats
        );
    }

    private BaselineSummary readBaseline(Element el) {
        return new BaselineSummary(
                el.getAttribute("source-file"),
                Instant.parse(el.getAttribute("generated-at")),
                Integer.parseInt(el.getAttribute("samples")),
                Integer.parseInt(el.getAttribute("successes")),
                Double.parseDouble(el.getAttribute("rate")),
                Double.parseDouble(el.getAttribute("derived-threshold"))
        );
    }

    private CovariateStatus readCovariates(Element el) {
        boolean aligned = Boolean.parseBoolean(el.getAttribute("aligned"));
        List<Misalignment> misalignments = new ArrayList<>();
        NodeList nodes = el.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "misalignment");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element m = (Element) nodes.item(i);
            misalignments.add(new Misalignment(
                    m.getAttribute("key"),
                    m.getAttribute("baseline-value"),
                    m.getAttribute("test-value")
            ));
        }
        return new CovariateStatus(aligned, misalignments);
    }

    private CostSummary readCost(Element el) {
        return new CostSummary(
                Long.parseLong(el.getAttribute("method-tokens")),
                Long.parseLong(el.getAttribute("method-time-budget-ms")),
                Long.parseLong(el.getAttribute("method-token-budget")),
                TokenMode.valueOf(el.getAttribute("token-mode")),
                optionalElement(el, "class-budget").map(this::readBudgetSnapshot),
                optionalElement(el, "suite-budget").map(this::readBudgetSnapshot)
        );
    }

    private BudgetSnapshot readBudgetSnapshot(Element el) {
        return new BudgetSnapshot(
                Long.parseLong(el.getAttribute("time-budget-ms")),
                Long.parseLong(el.getAttribute("elapsed-ms")),
                Long.parseLong(el.getAttribute("token-budget")),
                Long.parseLong(el.getAttribute("tokens-consumed"))
        );
    }

    private PacingSummary readPacing(Element el) {
        return new PacingSummary(
                Double.parseDouble(el.getAttribute("max-rps")),
                Double.parseDouble(el.getAttribute("max-rpm")),
                Double.parseDouble(el.getAttribute("max-rph")),
                Integer.parseInt(el.getAttribute("max-concurrent")),
                Long.parseLong(el.getAttribute("effective-min-delay-ms")),
                Integer.parseInt(el.getAttribute("effective-concurrency")),
                Double.parseDouble(el.getAttribute("effective-rps"))
        );
    }

    private SpecProvenance readProvenance(Element el) {
        String origin = el.getAttribute("origin");
        String contractRef = el.hasAttribute("contract-ref") ? el.getAttribute("contract-ref") : null;
        String specFilename = el.hasAttribute("spec-filename") ? el.getAttribute("spec-filename") : null;
        Optional<ExpirationInfo> expiration = optionalElement(el, "expiration")
                .map(this::readExpiration);
        Optional<String> baselineSourceLabel = optionalAttribute(el, "baseline-source");
        return new SpecProvenance(origin, contractRef, specFilename, expiration, baselineSourceLabel);
    }

    private ExpirationInfo readExpiration(Element el) {
        String statusName = el.getAttribute("status");
        Optional<Instant> expiresAt = optionalAttribute(el, "expires-at").map(Instant::parse);
        ExpirationStatus status = parseExpirationStatus(statusName);
        return new ExpirationInfo(status, expiresAt);
    }

    private ExpirationStatus parseExpirationStatus(String name) {
        return switch (name) {
            case "NO_EXPIRATION" -> ExpirationStatus.noExpiration();
            case "VALID" -> ExpirationStatus.valid(Duration.ZERO);
            case "EXPIRING_SOON" -> ExpirationStatus.expiringSoon(Duration.ZERO, 0.0);
            case "EXPIRING_IMMINENTLY" -> ExpirationStatus.expiringImminently(Duration.ZERO, 0.0);
            case "EXPIRED" -> ExpirationStatus.expired(Duration.ZERO);
            default -> throw new XmlReadException("Unknown expiration status: " + name, null);
        };
    }

    private Termination readTermination(Element el) {
        TerminationReason reason = TerminationReason.valueOf(el.getAttribute("reason"));
        Optional<String> details = optionalAttribute(el, "details");
        return new Termination(reason, details);
    }

    private Map<String, String> readEnvironment(Element root) {
        Optional<Element> envEl = optionalElement(root, "environment");
        if (envEl.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        NodeList entries = envEl.get().getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, "entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            result.put(entry.getAttribute("key"), entry.getAttribute("value"));
        }
        return result;
    }

    private List<String> readCaveats(Element parent) {
        Optional<Element> caveatsEl = optionalElement(parent, "caveats");
        if (caveatsEl.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        NodeList caveatNodes = caveatsEl.get().getElementsByTagNameNS(
                VerdictXmlWriter.NAMESPACE, "caveat");
        for (int i = 0; i < caveatNodes.getLength(); i++) {
            result.add(caveatNodes.item(i).getTextContent());
        }
        return result;
    }

    // ── DOM helpers ──────────────────────────────────────────────────────

    private Element firstElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, localName);
        if (nodes.getLength() == 0) {
            throw new XmlReadException("Missing required element: " + localName, null);
        }
        return (Element) nodes.item(0);
    }

    private Optional<Element> optionalElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(VerdictXmlWriter.NAMESPACE, localName);
        if (nodes.getLength() == 0) {
            return Optional.empty();
        }
        return Optional.of((Element) nodes.item(0));
    }

    private Optional<String> optionalAttribute(Element el, String name) {
        if (el.hasAttribute(name)) {
            return Optional.of(el.getAttribute(name));
        }
        return Optional.empty();
    }
}
