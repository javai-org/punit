/**
 * The punit-core module.
 *
 * <p>The framework's JUnit-free core: authoring API, statistical
 * engine, verdict pipeline, and the entry-point class {@code PUnit}.
 * Reachable from a sentinel-deployed classpath without JUnit on it
 * (JUnit annotation types are a compile-time-only dependency, via
 * {@code requires static}).
 *
 * <h2>Exported packages — the public surface</h2>
 *
 * <p>Every package listed via {@code exports X} is the public API
 * an author may import. The {@code internal.*} subtree is
 * intentionally not exported. Targeted-exports clauses
 * ({@code exports X to <module>}) below grant the sibling modules
 * ({@code punit-junit5}, {@code punit-report}, {@code punit-sentinel})
 * the minimum surface they need to compose with punit-core; external
 * consumers continue to see only the public packages.
 */
module org.javai.punit.core {

    // ── Public API surface ────────────────────────────────────
    exports org.javai.punit.api;
    exports org.javai.punit.api.criterion;
    exports org.javai.punit.api.spec;
    exports org.javai.punit.api.covariate;
    exports org.javai.punit.runtime;
    exports org.javai.punit.verdict;
    exports org.javai.punit.statistics;
    exports org.javai.punit.statistics.transparent;

    // ── Targeted exports — internal types granted to sibling
    //    modules at their narrowest. Each grant is the minimum
    //    sufficient set surfaced by the sibling's compile errors;
    //    none of these are visible to external (unnamed-module)
    //    consumers.
    exports org.javai.punit.internal.engine.emit
        to org.javai.punit.report;
    exports org.javai.punit.internal.reporting
        to org.javai.punit.report,
           org.javai.punit.sentinel;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.javai.outcome;
    requires transitive org.opentest4j;
    requires static org.junit.jupiter.api;
    requires java.xml;
    requires org.apache.commons.statistics.distribution;
    requires org.yaml.snakeyaml;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.csv;
    requires org.apache.logging.log4j;

    // ── ServiceLoader ─────────────────────────────────────────
    uses org.javai.punit.verdict.VerdictSink;
    uses org.javai.punit.api.spec.SpecCriterionDeriver;
    provides org.javai.punit.api.spec.SpecCriterionDeriver
        with org.javai.punit.internal.engine.criteria.PostureBasedSpecCriterionDeriver;
}
