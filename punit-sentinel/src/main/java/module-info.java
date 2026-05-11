/**
 * The punit-sentinel module.
 *
 * <p>Runtime engine for executing punit specs without a JUnit
 * classpath. Explicitly omits {@code requires org.junit.jupiter.api}
 * so the compiler enforces the JUnit-free invariant — a sentinel
 * deployment cannot accidentally drag in JUnit Jupiter or Platform.
 *
 * <p>{@code requires transitive org.javai.punit.core} re-exports
 * the framework's public API to sentinel consumers; they need not
 * declare a separate dependency on punit-core.
 */
module org.javai.punit.sentinel {

    // ── Public API surface ────────────────────────────────────
    exports org.javai.punit.sentinel;
    exports org.javai.punit.sentinel.verdict;

    // ── Required modules ──────────────────────────────────────
    requires transitive org.javai.punit.core;
    requires java.net.http;
    // Explicitly NO requires for JUnit — the compiler enforces
    // JUnit-freeness here, complementing SentinelArchitectureTest.
}
