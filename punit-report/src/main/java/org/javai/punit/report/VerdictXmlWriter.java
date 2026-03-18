package org.javai.punit.report;

import java.io.OutputStream;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.javai.punit.model.ExpirationStatus;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdict.*;

/**
 * Serialises a {@link ProbabilisticTestVerdict} to XML using {@link XMLStreamWriter}.
 *
 * <p>The verdict model in punit-core remains annotation-free — this class owns the
 * mapping from records to XML elements.
 */
public final class VerdictXmlWriter {

    static final String NAMESPACE = "http://javai.org/punit/report";
    static final String VERSION = "1.0";

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    /**
     * Writes the verdict as XML to the given output stream.
     * The stream is not closed by this method.
     */
    public void write(ProbabilisticTestVerdict verdict, OutputStream out) throws XMLStreamException {
        XMLStreamWriter w = OUTPUT_FACTORY.createXMLStreamWriter(out, "UTF-8");
        try {
            w.writeStartDocument("UTF-8", "1.0");
            writeVerdict(w, verdict);
            w.writeEndDocument();
            w.flush();
        } finally {
            w.close();
        }
    }

    private void writeVerdict(XMLStreamWriter w, ProbabilisticTestVerdict v) throws XMLStreamException {
        w.writeStartElement("punit-verdict");
        w.writeDefaultNamespace(NAMESPACE);
        w.writeAttribute("version", VERSION);
        w.writeAttribute("timestamp", v.timestamp().toString());
        w.writeAttribute("correlation-id", v.correlationId());

        writeIdentity(w, v.identity());
        writeExecution(w, v.execution());
        v.functional().ifPresent(f -> writeFunctionalUnchecked(w, f));
        v.latency().ifPresent(l -> writeLatencyUnchecked(w, l));
        writeStatistics(w, v.statistics());
        writeCovariates(w, v.covariates());
        writeCost(w, v.cost());
        v.pacing().ifPresent(p -> writePacingUnchecked(w, p));
        v.provenance().ifPresent(p -> writeProvenanceUnchecked(w, p));
        writeTermination(w, v.termination());
        writeEnvironment(w, v.environmentMetadata());
        writeVerdictElement(w, v);

        w.writeEndElement();
    }

    private void writeIdentity(XMLStreamWriter w, TestIdentity id) throws XMLStreamException {
        w.writeStartElement("identity");
        w.writeAttribute("class-name", id.className());
        w.writeAttribute("method-name", id.methodName());
        id.useCaseId().ifPresent(uc -> writeAttributeUnchecked(w, "use-case-id", uc));
        w.writeEndElement();
    }

    private void writeExecution(XMLStreamWriter w, ExecutionSummary exec) throws XMLStreamException {
        w.writeStartElement("execution");
        w.writeAttribute("planned-samples", Integer.toString(exec.plannedSamples()));
        w.writeAttribute("samples-executed", Integer.toString(exec.samplesExecuted()));
        w.writeAttribute("successes", Integer.toString(exec.successes()));
        w.writeAttribute("failures", Integer.toString(exec.failures()));
        w.writeAttribute("min-pass-rate", formatDouble(exec.minPassRate()));
        w.writeAttribute("observed-pass-rate", formatDouble(exec.observedPassRate()));
        w.writeAttribute("elapsed-ms", Long.toString(exec.elapsedMs()));
        exec.appliedMultiplier().ifPresent(m -> writeAttributeUnchecked(w, "applied-multiplier", formatDouble(m)));
        w.writeAttribute("intent", exec.intent().name());
        w.writeAttribute("confidence", formatDouble(exec.resolvedConfidence()));
        if (exec.warmup() > 0) {
            w.writeAttribute("warmup", Integer.toString(exec.warmup()));
        }
        w.writeEndElement();
    }

    private void writeFunctional(XMLStreamWriter w, FunctionalDimension func) throws XMLStreamException {
        w.writeStartElement("functional");
        w.writeAttribute("successes", Integer.toString(func.successes()));
        w.writeAttribute("failures", Integer.toString(func.failures()));
        w.writeAttribute("pass-rate", formatDouble(func.passRate()));
        w.writeEndElement();
    }

    private void writeLatency(XMLStreamWriter w, LatencyDimension lat) throws XMLStreamException {
        w.writeStartElement("latency");
        w.writeAttribute("successful-samples", Integer.toString(lat.successfulSamples()));
        w.writeAttribute("total-samples", Integer.toString(lat.totalSamples()));
        w.writeAttribute("skipped", Boolean.toString(lat.skipped()));
        lat.skipReason().ifPresent(r -> writeAttributeUnchecked(w, "skip-reason", r));
        w.writeAttribute("dimension-successes", Integer.toString(lat.dimensionSuccesses()));
        w.writeAttribute("dimension-failures", Integer.toString(lat.dimensionFailures()));

        if (!lat.skipped()) {
            writeDistribution(w, lat);
            writeAssertions(w, lat);
        }
        writeCaveats(w, lat.caveats());

        w.writeEndElement();
    }

    private void writeDistribution(XMLStreamWriter w, LatencyDimension lat) throws XMLStreamException {
        w.writeStartElement("distribution");
        w.writeAttribute("p50", Long.toString(lat.p50Ms()));
        w.writeAttribute("p90", Long.toString(lat.p90Ms()));
        w.writeAttribute("p95", Long.toString(lat.p95Ms()));
        w.writeAttribute("p99", Long.toString(lat.p99Ms()));
        w.writeAttribute("max", Long.toString(lat.maxMs()));
        w.writeEndElement();
    }

    private void writeAssertions(XMLStreamWriter w, LatencyDimension lat) throws XMLStreamException {
        if (lat.assertions().isEmpty()) {
            return;
        }
        w.writeStartElement("assertions");
        for (PercentileAssertion pa : lat.assertions()) {
            w.writeStartElement("percentile");
            w.writeAttribute("label", pa.label());
            w.writeAttribute("observed", Long.toString(pa.observedMs()));
            w.writeAttribute("threshold", Long.toString(pa.thresholdMs()));
            w.writeAttribute("passed", Boolean.toString(pa.passed()));
            w.writeAttribute("indicative", Boolean.toString(pa.indicative()));
            if (pa.source() != null) {
                w.writeAttribute("source", pa.source());
            }
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeStatistics(XMLStreamWriter w, StatisticalAnalysis stats) throws XMLStreamException {
        w.writeStartElement("statistics");
        w.writeAttribute("confidence-level", formatDouble(stats.confidenceLevel()));
        w.writeAttribute("standard-error", formatDouble(stats.standardError()));
        w.writeAttribute("ci-lower", formatDouble(stats.ciLower()));
        w.writeAttribute("ci-upper", formatDouble(stats.ciUpper()));
        stats.testStatistic().ifPresent(t -> writeAttributeUnchecked(w, "test-statistic", formatDouble(t)));
        stats.pValue().ifPresent(p -> writeAttributeUnchecked(w, "p-value", formatDouble(p)));
        stats.thresholdDerivation().ifPresent(d -> writeAttributeUnchecked(w, "threshold-derivation", d));

        stats.baseline().ifPresent(b -> writeBaselineUnchecked(w, b));
        writeCaveats(w, stats.caveats());

        w.writeEndElement();
    }

    private void writeBaseline(XMLStreamWriter w, BaselineSummary b) throws XMLStreamException {
        w.writeStartElement("baseline");
        w.writeAttribute("source-file", b.sourceFile());
        w.writeAttribute("generated-at", b.generatedAt().toString());
        w.writeAttribute("samples", Integer.toString(b.baselineSamples()));
        w.writeAttribute("successes", Integer.toString(b.baselineSuccesses()));
        w.writeAttribute("rate", formatDouble(b.baselineRate()));
        w.writeAttribute("derived-threshold", formatDouble(b.derivedThreshold()));
        w.writeEndElement();
    }

    private void writeCovariates(XMLStreamWriter w, CovariateStatus cov) throws XMLStreamException {
        w.writeStartElement("covariates");
        w.writeAttribute("aligned", Boolean.toString(cov.aligned()));
        for (Misalignment m : cov.misalignments()) {
            w.writeStartElement("misalignment");
            w.writeAttribute("key", m.covariateKey());
            w.writeAttribute("baseline-value", m.baselineValue());
            w.writeAttribute("test-value", m.testValue());
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeCost(XMLStreamWriter w, CostSummary cost) throws XMLStreamException {
        w.writeStartElement("cost");
        w.writeAttribute("method-tokens", Long.toString(cost.methodTokensConsumed()));
        w.writeAttribute("method-time-budget-ms", Long.toString(cost.methodTimeBudgetMs()));
        w.writeAttribute("method-token-budget", Long.toString(cost.methodTokenBudget()));
        w.writeAttribute("token-mode", cost.tokenMode().name());
        cost.classBudget().ifPresent(b -> writeBudgetSnapshotUnchecked(w, "class-budget", b));
        cost.suiteBudget().ifPresent(b -> writeBudgetSnapshotUnchecked(w, "suite-budget", b));
        w.writeEndElement();
    }

    private void writeBudgetSnapshot(XMLStreamWriter w, String elementName, BudgetSnapshot b) throws XMLStreamException {
        w.writeStartElement(elementName);
        w.writeAttribute("time-budget-ms", Long.toString(b.timeBudgetMs()));
        w.writeAttribute("elapsed-ms", Long.toString(b.elapsedMs()));
        w.writeAttribute("token-budget", Long.toString(b.tokenBudget()));
        w.writeAttribute("tokens-consumed", Long.toString(b.tokensConsumed()));
        w.writeEndElement();
    }

    private void writePacing(XMLStreamWriter w, PacingSummary p) throws XMLStreamException {
        w.writeStartElement("pacing");
        w.writeAttribute("max-rps", formatDouble(p.maxRequestsPerSecond()));
        w.writeAttribute("max-rpm", formatDouble(p.maxRequestsPerMinute()));
        w.writeAttribute("max-rph", formatDouble(p.maxRequestsPerHour()));
        w.writeAttribute("max-concurrent", Integer.toString(p.maxConcurrentRequests()));
        w.writeAttribute("effective-min-delay-ms", Long.toString(p.effectiveMinDelayMs()));
        w.writeAttribute("effective-concurrency", Integer.toString(p.effectiveConcurrency()));
        w.writeAttribute("effective-rps", formatDouble(p.effectiveRps()));
        w.writeEndElement();
    }

    private void writeProvenance(XMLStreamWriter w, SpecProvenance prov) throws XMLStreamException {
        w.writeStartElement("provenance");
        w.writeAttribute("origin", prov.thresholdOriginName());
        if (prov.contractRef() != null) {
            w.writeAttribute("contract-ref", prov.contractRef());
        }
        if (prov.specFilename() != null) {
            w.writeAttribute("spec-filename", prov.specFilename());
        }
        prov.baselineSourceLabel().ifPresent(label ->
                writeAttributeUnchecked(w, "baseline-source", label));
        prov.expiration().ifPresent(e -> writeExpirationUnchecked(w, e));
        w.writeEndElement();
    }

    private void writeExpiration(XMLStreamWriter w, ExpirationInfo info) throws XMLStreamException {
        w.writeStartElement("expiration");
        w.writeAttribute("status", expirationStatusName(info.status()));
        info.expiresAt().ifPresent(t -> writeAttributeUnchecked(w, "expires-at", t.toString()));
        w.writeAttribute("requires-warning", Boolean.toString(info.status().requiresWarning()));
        w.writeEndElement();
    }

    private void writeTermination(XMLStreamWriter w, Termination term) throws XMLStreamException {
        w.writeStartElement("termination");
        w.writeAttribute("reason", term.reason().name());
        term.details().ifPresent(d -> writeAttributeUnchecked(w, "details", d));
        w.writeEndElement();
    }

    private void writeEnvironment(XMLStreamWriter w, Map<String, String> metadata) throws XMLStreamException {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        w.writeStartElement("environment");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            w.writeStartElement("entry");
            w.writeAttribute("key", entry.getKey());
            w.writeAttribute("value", entry.getValue());
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private void writeVerdictElement(XMLStreamWriter w, ProbabilisticTestVerdict v) throws XMLStreamException {
        w.writeStartElement("verdict");
        w.writeAttribute("junit-passed", Boolean.toString(v.junitPassed()));
        w.writeAttribute("punit-verdict", v.punitVerdict().name());
        w.writeEndElement();
    }

    private void writeCaveats(XMLStreamWriter w, java.util.List<String> caveats) throws XMLStreamException {
        if (caveats == null || caveats.isEmpty()) {
            return;
        }
        w.writeStartElement("caveats");
        for (String caveat : caveats) {
            w.writeStartElement("caveat");
            w.writeCharacters(caveat);
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    // ── ExpirationStatus mapping ─────────────────────────────────────────

    private static String expirationStatusName(ExpirationStatus status) {
        return switch (status) {
            case ExpirationStatus.NoExpiration n -> "NO_EXPIRATION";
            case ExpirationStatus.Valid v -> "VALID";
            case ExpirationStatus.ExpiringSoon s -> "EXPIRING_SOON";
            case ExpirationStatus.ExpiringImminently i -> "EXPIRING_IMMINENTLY";
            case ExpirationStatus.Expired e -> "EXPIRED";
        };
    }

    // ── Formatting ───────────────────────────────────────────────────────

    private static String formatDouble(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    // ── Unchecked wrappers (XMLStreamException → RuntimeException) ───────

    private static void writeAttributeUnchecked(XMLStreamWriter w, String name, String value) {
        try {
            w.writeAttribute(name, value);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write attribute: " + name, e);
        }
    }

    private void writeFunctionalUnchecked(XMLStreamWriter w, FunctionalDimension f) {
        try {
            writeFunctional(w, f);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write functional dimension", e);
        }
    }

    private void writeLatencyUnchecked(XMLStreamWriter w, LatencyDimension l) {
        try {
            writeLatency(w, l);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write latency dimension", e);
        }
    }

    private void writePacingUnchecked(XMLStreamWriter w, PacingSummary p) {
        try {
            writePacing(w, p);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write pacing", e);
        }
    }

    private void writeProvenanceUnchecked(XMLStreamWriter w, SpecProvenance p) {
        try {
            writeProvenance(w, p);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write provenance", e);
        }
    }

    private void writeExpirationUnchecked(XMLStreamWriter w, ExpirationInfo e) {
        try {
            writeExpiration(w, e);
        } catch (XMLStreamException ex) {
            throw new XmlWriteException("Failed to write expiration", ex);
        }
    }

    private void writeBaselineUnchecked(XMLStreamWriter w, BaselineSummary b) {
        try {
            writeBaseline(w, b);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write baseline", e);
        }
    }

    private void writeBudgetSnapshotUnchecked(XMLStreamWriter w, String elementName, BudgetSnapshot b) {
        try {
            writeBudgetSnapshot(w, elementName, b);
        } catch (XMLStreamException e) {
            throw new XmlWriteException("Failed to write budget snapshot: " + elementName, e);
        }
    }
}
