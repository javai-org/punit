/**
 * The punit-report module.
 *
 * <p>Verdict XML reader/writer and HTML report generation. Registers
 * its {@code VerdictXmlSink} via {@code provides} so that
 * {@code punit-core}'s {@code uses VerdictSink} discovers it
 * automatically when this module is on the modulepath.
 */
module org.javai.punit.report {

    // ── Public API surface ────────────────────────────────────
    exports org.javai.punit.report;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.javai.punit.core;
    requires java.xml;
    requires org.apache.logging.log4j;

    // ── ServiceLoader: register the XML verdict sink ──────────
    provides org.javai.punit.verdict.VerdictSink
        with org.javai.punit.report.VerdictXmlSink;
}
