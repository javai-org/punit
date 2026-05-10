package org.javai.punit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Requirement-code isolation rule.
 *
 * <p>The orchestrator catalog tracks features by short letter-prefix
 * codes (CT04, EX04, LT01, RP01, and similar). Those codes are
 * orchestrator-internal: they are not interpretable by a reader of
 * punit's open-source code, who has no orchestrator access. Per the
 * project family's convention, the codes must not leak into punit's
 * production source (javadoc, inline comments, code, string literals):
 * a feature gets referred to by its domain name, not by its tracking
 * code.
 *
 * <p>This test walks every Java source file under the project's
 * <code>src/main/java</code> trees and asserts no requirement-style
 * code appears anywhere - neither in code nor in comments. It is the
 * regression guard for the convention; any commit that reintroduces a
 * code fails CI.
 *
 * <p>Wire-format constants (<code>"punit-baseline-2"</code>,
 * <code>"verdict-1.0"</code>, and similar) are not requirement codes:
 * they have a different shape (lowercase prefix, not two uppercase
 * letters) and the regex below does not match them.
 */
@DisplayName("Requirement-code isolation")
class RequirementCodeIsolationTest {

    /**
     * Matches the catalog's letter-prefix-plus-two-digit convention.
     * Letters cover every prefix currently in use across the
     * orchestrator catalog and design docs:
     * <ul>
     *   <li>CT - conformance testing</li>
     *   <li>EX - experiments</li>
     *   <li>LT - latency dimension</li>
     *   <li>PT - probabilistic tests</li>
     *   <li>RC - resource controls</li>
     *   <li>RP - reporting</li>
     *   <li>SC - statistics core</li>
     *   <li>SN - sentinel</li>
     *   <li>TH - test-harness integration</li>
     *   <li>UC - use-case model</li>
     *   <li>XM - examples</li>
     *   <li>DG - design guides (orchestrator docs/)</li>
     * </ul>
     *
     * <p>Two letters then exactly two digits, with word boundaries on
     * either side. Matches RP01, LT04, EX10; does not match
     * FT/IT/OT type-parameter conventions, nor lowercase-prefixed
     * wire-format constants.
     */
    private static final Pattern REQUIREMENT_CODE = Pattern.compile(
            "\\b(CT|EX|LT|PT|RC|RP|SC|SN|TH|UC|XM|DG)\\d{2}\\b");

    /**
     * Module src/main/java roots, relative to the project root the
     * Gradle test task runs in (which is the per-module dir: this
     * test lives in punit-core, so its CWD is .../punit/punit-core).
     * Walk up one level to reach the project root, then descend
     * per-module.
     */
    private static final List<Path> PRODUCTION_SOURCE_ROOTS = List.of(
            Paths.get("..", "punit-core", "src", "main", "java"),
            Paths.get("..", "punit-report", "src", "main", "java"),
            Paths.get("..", "punit-sentinel", "src", "main", "java"));

    @Test
    @DisplayName("no orchestrator-internal requirement codes appear in production source")
    void noLeaksInProductionSource() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path root : PRODUCTION_SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) {
                throw new AssertionError(
                        "Production source root not found: " + root.toAbsolutePath()
                                + " - the test must run from the punit-core module directory.");
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(file -> scanFile(file, hits));
            }
        }
        assertThat(hits)
                .as("Production source must be free of orchestrator requirement codes "
                        + "(per the project family's convention; see CLAUDE.md). "
                        + "Replace each match with a domain-language description "
                        + "of the feature, or drop the cross-reference if it adds "
                        + "no information beyond what the surrounding context already gives.")
                .isEmpty();
    }

    private static void scanFile(Path file, List<String> hits) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to read " + file, e);
        }
        Matcher matcher = REQUIREMENT_CODE.matcher(content);
        while (matcher.find()) {
            int line = lineNumberOf(content, matcher.start());
            hits.add(file.normalize() + ":" + line + " - " + matcher.group());
        }
    }

    private static int lineNumberOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
