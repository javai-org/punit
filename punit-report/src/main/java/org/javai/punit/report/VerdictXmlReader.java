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
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.*;
import org.javai.punit.verdict.PUnitVerdict;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Deserialises a {@link ProbabilisticTestVerdict} from verdict XML.
 *
 * <p>Reads the {@code http://javai.org/verdict/1.0} format and maps it back
 * to the punit verdict model. PUnit-specific fields not present in the verdict-XML standard
 * (pacing, environment, expiration, correlation ID) receive default values.
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
            return readVerdictRecord(doc.getDocumentElement());
        } catch (Exception e) {
            throw new XmlReadException("Failed to read verdict XML", e);
        }
    }

    private ProbabilisticTestVerdict readVerdictRecord(Element root) {
        Instant timestamp = Instant.parse(root.getAttribute("timestamp"));
        String correlationId = optionalAttribute(root, "correlation-id")
                .orElse("");

        TestIdentity identity = readIdentity(firstElement(root, "identity"));
        ExecutionSummary execution = readExecution(firstElement(root, "execution"));
        Optional<FunctionalDimension> functional = optionalElement(root, "functional")
                .map(this::readFunctional);
        Optional<LatencyDimension> latency = optionalElement(root, "latency")
                .map(this::readLatency);
        StatisticalAnalysis statistics = readStatistics(root);
        CovariateStatus covariates = readCovariates(firstElement(root, "covariates"));
        Optional<SpecProvenance> provenance = optionalElement(root, "provenance")
                .map(this::readProvenance);
        Optional<PacingSummary> pacing = optionalElement(root, "pacing")
                .map(this::readPacing);
        Termination termination = readTermination(firstElement(root, "termination"));
        Map<String, String> environment = readEnvironment(root);

        Element verdictEl = firstElement(root, "verdict");
        PUnitVerdict punitVerdict = PUnitVerdict.valueOf(verdictEl.getAttribute("value"));
        String verdictReason = optionalAttribute(verdictEl, "reason").orElse("");
        boolean junitPassed = punitVerdict != PUnitVerdict.FAIL;

        return new ProbabilisticTestVerdict(
                correlationId, timestamp, identity, execution,
                functional, latency, statistics, covariates,
                new CostSummary(0, 0, 0, TokenMode.NONE, Optional.empty(), Optional.empty()),
                pacing,
                provenance,
                termination,
                environment,
                junitPassed, punitVerdict, verdictReason
        );
    }

    private TestIdentity readIdentity(Element el) {
        String useCaseId = el.getAttribute("use-case-id");
        Optional<String> testName = optionalAttribute(el, "test-name");
        // Map verdict-XML identity back to punit's class-name / method-name model
        String className = useCaseId;
        String methodName = testName.orElse(useCaseId);
        return new TestIdentity(className, methodName, Optional.of(useCaseId));
    }

    private ExecutionSummary readExecution(Element el) {
        int plannedSamples = Integer.parseInt(el.getAttribute("planned-samples"));
        int samplesExecuted = Integer.parseInt(el.getAttribute("samples-executed"));
        int successes = Integer.parseInt(el.getAttribute("successes"));
        int failures = Integer.parseInt(el.getAttribute("failures"));
        long elapsedMs = Long.parseLong(el.getAttribute("elapsed-ms"));
        TestIntent intent = TestIntent.valueOf(el.getAttribute("intent"));
        double confidence = Double.parseDouble(el.getAttribute("confidence"));
        int warmup = optionalAttribute(el, "warmup")
                .map(Integer::parseInt).orElse(0);
        double observedPassRate = samplesExecuted > 0
                ? (double) successes / samplesExecuted : 0.0;
        return new ExecutionSummary(
                plannedSamples, samplesExecuted, successes, failures,
                0.0, // min-pass-rate not in the verdict-XML standard, filled from statistics threshold
                observedPassRate,
                elapsedMs, Optional.empty(), intent, confidence,
                new UseCaseAttributes(warmup)
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

        // Read observed percentiles
        long p50 = 0, p90 = 0, p95 = 0, p99 = 0;
        Optional<Element> observedEl = optionalElement(el, "observed");
        if (observedEl.isPresent()) {
            NodeList percentiles = observedEl.get().getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "percentile");
            for (int i = 0; i < percentiles.getLength(); i++) {
                Element p = (Element) percentiles.item(i);
                long valueMs = Long.parseLong(p.getAttribute("value-ms"));
                switch (p.getAttribute("label")) {
                    case "p50" -> p50 = valueMs;
                    case "p90" -> p90 = valueMs;
                    case "p95" -> p95 = valueMs;
                    case "p99" -> p99 = valueMs;
                    default -> {}
                }
            }
        }

        // Read evaluations
        List<PercentileAssertion> assertions = new ArrayList<>();
        Optional<Element> evalsEl = optionalElement(el, "evaluations");
        if (evalsEl.isPresent()) {
            NodeList evals = evalsEl.get().getElementsByTagNameNS(
                    VerdictXmlWriter.NAMESPACE, "evaluation");
            for (int i = 0; i < evals.getLength(); i++) {
                Element e = (Element) evals.item(i);
                String status = e.getAttribute("status");
                boolean passed = "PASS".equals(status);
                boolean indicative = "advisory".equals(e.getAttribute("mode"));
                String source = "baseline-derived".equals(e.getAttribute("provenance"))
                        ? "from baseline" : null;
                assertions.add(new PercentileAssertion(
                        e.getAttribute("percentile"),
                        Long.parseLong(e.getAttribute("observed-ms")),
                        Long.parseLong(e.getAttribute("threshold-ms")),
                        passed, indicative, source
                ));
            }
        }

        int strictViolations = Integer.parseInt(el.getAttribute("strict-violations"));
        int advisoryViolations = Integer.parseInt(el.getAttribute("advisory-violations"));
        int dimensionFailures = strictViolations + advisoryViolations;
        int dimensionSuccesses = assertions.size() - dimensionFailures;

        return new LatencyDimension(
                successfulSamples, successfulSamples, false, Optional.empty(),
                p50, p90, p95, p99, Math.max(Math.max(p95, p99), p50),
                assertions, List.of(),
                Math.max(dimensionSuccesses, 0), dimensionFailures
        );
    }

    private StatisticalAnalysis readStatistics(Element root) {
        Element el = firstElement(root, "statistics");
        Optional<BaselineSummary> baseline = optionalElement(root, "baseline")
                .map(this::readBaseline);
        List<String> caveats = readWarnings(root);

        return new StatisticalAnalysis(
                Double.parseDouble(el.getAttribute("confidence-level")),
                Double.parseDouble(el.getAttribute("standard-error")),
                Double.parseDouble(el.getAttribute("ci-lower")),
                Double.parseDouble(el.getAttribute("ci-upper")),
                optionalAttribute(el, "test-statistic").map(Double::parseDouble),
                optionalAttribute(el, "p-value").map(Double::parseDouble),
                Optional.empty(), // threshold-derivation
                baseline,
                caveats
        );
    }

    private BaselineSummary readBaseline(Element el) {
        return new BaselineSummary(
                el.getAttribute("source-file"),
                Instant.parse(el.getAttribute("generated-at")),
                Integer.parseInt(el.getAttribute("samples")),
                0, // successes not in the verdict-XML standard baseline
                Double.parseDouble(el.getAttribute("baseline-rate")),
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
                    m.getAttribute("observed-value")
            ));
        }
        return new CovariateStatus(aligned, misalignments, Map.of(), Map.of());
    }

    private SpecProvenance readProvenance(Element el) {
        String origin = el.getAttribute("origin");
        String contractRef = el.hasAttribute("contract-ref") ? el.getAttribute("contract-ref") : null;
        String specFilename = el.hasAttribute("spec-filename") ? el.getAttribute("spec-filename") : null;
        Optional<ExpirationInfo> expiration = optionalElement(el, "expiration")
                .map(this::readExpiration);
        return new SpecProvenance(origin, contractRef, specFilename,
                expiration, Optional.empty());
    }

    private ExpirationInfo readExpiration(Element el) {
        String statusName = el.getAttribute("status");
        Optional<Instant> expiresAt = optionalAttribute(el, "expires-at").map(Instant::parse);
        org.javai.punit.model.ExpirationStatus status = parseExpirationStatus(statusName);
        return new ExpirationInfo(status, expiresAt);
    }

    private org.javai.punit.model.ExpirationStatus parseExpirationStatus(String name) {
        return switch (name) {
            case "NO_EXPIRATION" -> org.javai.punit.model.ExpirationStatus.noExpiration();
            case "VALID" -> org.javai.punit.model.ExpirationStatus.valid(Duration.ZERO);
            case "EXPIRING_SOON" -> org.javai.punit.model.ExpirationStatus.expiringSoon(Duration.ZERO, 0.0);
            case "EXPIRING_IMMINENTLY" -> org.javai.punit.model.ExpirationStatus.expiringImminently(Duration.ZERO, 0.0);
            case "EXPIRED" -> org.javai.punit.model.ExpirationStatus.expired(Duration.ZERO);
            default -> throw new XmlReadException("Unknown expiration status: " + name, null);
        };
    }

    private PacingSummary readPacing(Element el) {
        return new PacingSummary(
                Double.parseDouble(el.getAttribute("max-rps")),
                Double.parseDouble(el.getAttribute("max-rpm")),
                0.0, // max-rph not in the verdict-XML standard
                Integer.parseInt(el.getAttribute("max-concurrent")),
                Long.parseLong(el.getAttribute("effective-min-delay-ms")),
                Integer.parseInt(el.getAttribute("effective-concurrency")),
                Double.parseDouble(el.getAttribute("effective-rps"))
        );
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

    private Termination readTermination(Element el) {
        String reason = el.getAttribute("reason");
        Optional<String> detail = optionalAttribute(el, "detail");
        TerminationReason mapped = switch (reason) {
            case "COMPLETED" -> TerminationReason.COMPLETED;
            case "FAILURE_INEVITABLE" -> TerminationReason.IMPOSSIBILITY;
            case "SUCCESS_GUARANTEED" -> TerminationReason.SUCCESS_GUARANTEED;
            case "TIME_BUDGET_EXHAUSTED" -> TerminationReason.METHOD_TIME_BUDGET_EXHAUSTED;
            case "TOKEN_BUDGET_EXHAUSTED" -> TerminationReason.METHOD_TOKEN_BUDGET_EXHAUSTED;
            default -> TerminationReason.COMPLETED;
        };
        return new Termination(mapped, detail);
    }

    private List<String> readWarnings(Element root) {
        Optional<Element> warningsEl = optionalElement(root, "warnings");
        if (warningsEl.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        NodeList warnings = warningsEl.get().getElementsByTagNameNS(
                VerdictXmlWriter.NAMESPACE, "warning");
        for (int i = 0; i < warnings.getLength(); i++) {
            result.add(warnings.item(i).getTextContent());
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
