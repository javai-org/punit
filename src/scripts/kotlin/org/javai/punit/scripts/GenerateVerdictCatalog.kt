package org.javai.punit.scripts

import java.io.File

/**
 * Assembles `docs/VERDICT-CATALOG.md` from the two generated verdict catalogue
 * files (`build/verdict-catalogue-SUMMARY.md` and `build/verdict-catalogue-VERBOSE.md`).
 *
 * Each generated file is produced by [VerdictCatalogueTest] running under a specific
 * `punit.stats.detailLevel`. This script parses both files, extracts named blocks,
 * and weaves them into the final document using a template with section prose.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Block types recognised in the generated files
// ─────────────────────────────────────────────────────────────────────────────

private enum class BlockType {
    BASELINE_FOUND,
    TEST_CONFIGURATION,
    VERDICT,
    STATISTICAL_ANALYSIS;

    companion object {
        fun from(title: String): BlockType = when {
            title.startsWith("BASELINE FOUND") -> BASELINE_FOUND
            title.startsWith("TEST CONFIGURATION") -> TEST_CONFIGURATION
            title.startsWith("VERDICT:") -> VERDICT
            title.startsWith("STATISTICAL ANALYSIS") -> STATISTICAL_ANALYSIS
            else -> error("Unrecognised block title: $title")
        }
    }
}

/**
 * A single block from the generated catalogue file.
 *
 * @param title     The extracted title (e.g. "VERDICT: PASS")
 * @param type      The classified block type
 * @param method    The test method name extracted from the title (e.g. "servicePassesComfortably")
 * @param codeBlock The full content inside the fenced code block (including the banner)
 */
private data class Block(
    val title: String,
    val type: BlockType,
    val method: String,
    val codeBlock: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// Parsing
// ─────────────────────────────────────────────────────────────────────────────

/** Pattern matching the `═ TITLE ═══...` banner lines produced by PUnit. */
private val TITLE_PATTERN = Regex("^═ (.+?) ═+.*$", RegexOption.MULTILINE)

/**
 * Extracts the test method name from a block title.
 *
 * Block titles take forms like:
 * - `TEST CONFIGURATION FOR: methodName`
 * - `VERDICT: PASS` (method name on the next line inside the code block)
 * - `STATISTICAL ANALYSIS FOR: methodName(UseCaseClass...`
 * - `BASELINE FOUND FOR USE CASE: UseCaseName`
 *
 * For VERDICT blocks the method name is on the first non-blank content line.
 * For BASELINE FOUND blocks the method is not in the title — it is inferred
 * from adjacency (the next TEST_CONFIGURATION block's method).
 */
private fun extractMethodName(title: String, codeBlock: String, type: BlockType): String {
    return when (type) {
        BlockType.TEST_CONFIGURATION -> {
            // "TEST CONFIGURATION FOR: methodName" → methodName
            title.substringAfter("FOR:").trim()
        }
        BlockType.STATISTICAL_ANALYSIS -> {
            // "STATISTICAL ANALYSIS FOR: methodName(UseCase..." → methodName
            // Titles may be truncated with "..." when the method name is long
            val afterFor = title.substringAfter("FOR:").trim()
            afterFor.substringBefore("(").removeSuffix("...").trim()
        }
        BlockType.VERDICT -> {
            // Method name is on the first non-blank content line of the code block
            // e.g. "  servicePassesComfortably(ShoppingBasketUseCase, String)"
            val lines = codeBlock.lines()
            // Skip blank lines and the banner line, find the first content line
            val contentLine = lines
                .dropWhile { it.isBlank() || it.startsWith("═") }
                .firstOrNull { it.isNotBlank() }
                ?.trim() ?: error("Cannot find method name in VERDICT block")
            contentLine.substringBefore("(").trim()
        }
        BlockType.BASELINE_FOUND -> {
            // "BASELINE FOUND FOR USE CASE: UseCaseName" — we use a placeholder;
            // the method is resolved by adjacency later
            "__BASELINE__"
        }
    }
}

/**
 * Parses a generated catalogue file into a list of [Block]s.
 *
 * The file format (produced by `CatalogueMarkdownAppender`) is:
 * ```
 * # PUnit Verdict Catalogue
 * Generated: ...
 * Detail level: ...
 *
 * ---
 *
 * ## TITLE
 *
 * ``` (code block)
 * ... content ...
 * ``` (end code block)
 *
 * ---
 *
 * ## TITLE
 * ...
 * ```
 */
private fun parseBlocks(file: File): List<Block> {
    val content = file.readText()
    // Split on "---" separators
    val sections = content.split("\n---\n").drop(1) // drop the header section

    val blocks = mutableListOf<Block>()

    for (section in sections) {
        val trimmed = section.trim()
        if (trimmed.isEmpty()) continue

        // Extract the markdown heading (## TITLE)
        val headingLine = trimmed.lines().firstOrNull { it.startsWith("## ") } ?: continue
        val title = headingLine.removePrefix("## ").trim()

        // Extract the code block content
        val codeStart = trimmed.indexOf("```\n")
        val codeEnd = trimmed.lastIndexOf("\n```")
        if (codeStart < 0 || codeEnd < 0 || codeEnd <= codeStart) continue

        val codeBlock = trimmed.substring(codeStart + 4, codeEnd)

        val type = BlockType.from(title)
        val method = extractMethodName(title, codeBlock, type)

        blocks.add(Block(title, type, method, codeBlock))
    }

    // Resolve BASELINE_FOUND methods from adjacency: assign the method of the
    // next TEST_CONFIGURATION or STATISTICAL_ANALYSIS block
    for (i in blocks.indices) {
        if (blocks[i].method == "__BASELINE__" && i + 1 < blocks.size) {
            blocks[i] = blocks[i].copy(method = blocks[i + 1].method)
        }
    }

    return blocks
}

// ─────────────────────────────────────────────────────────────────────────────
// Block lookup
// ─────────────────────────────────────────────────────────────────────────────

private class BlockIndex(blocks: List<Block>) {
    private val byMethodAndType: Map<Pair<String, BlockType>, Block> =
        blocks.associateBy { it.method to it.type }

    fun get(method: String, type: BlockType): Block =
        byMethodAndType[method to type]
            ?: error("Block not found: method=$method, type=$type. Available: ${byMethodAndType.keys}")

    fun getOrNull(method: String, type: BlockType): Block? =
        byMethodAndType[method to type]
}

private fun fenced(block: Block): String = "```\n${block.codeBlock}\n```"

// ─────────────────────────────────────────────────────────────────────────────
// Section definitions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Describes one section of the verdict catalogue document.
 *
 * @param number     Section number (1-based)
 * @param heading    Section heading text (without the `## N. ` prefix)
 * @param intro      Prose paragraph(s) introducing the section
 * @param parts      Ordered list of content parts to emit
 */
private data class Section(
    val number: Int,
    val heading: String,
    val intro: String,
    val parts: List<Part>,
)

private sealed interface Part {
    /** Emit a `### Summary` or `### Verbose` subheading */
    data class Subheading(val text: String) : Part

    /** Emit a code block from a specific method/type/level */
    data class CodeBlock(val level: String, val method: String, val type: BlockType) : Part

    /** Emit a prose paragraph between blocks */
    data class Prose(val text: String) : Part
}

private fun sections(): List<Section> = listOf(
    Section(
        number = 1,
        heading = "Pass",
        intro = "The simplest passing verdict. PUnit detects that the required number of " +
                "successes has been reached and terminates early, skipping the remaining samples.",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.CodeBlock("SUMMARY", "servicePassesComfortably", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "servicePassesComfortably", BlockType.VERDICT),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "servicePassesComfortablyTransparent", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
    Section(
        number = 2,
        heading = "Fail with early termination",
        intro = "When PUnit determines that the threshold can no longer be reached — even if " +
                "every remaining sample succeeds — it terminates early and reports the " +
                "impossibility analysis.",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.CodeBlock("SUMMARY", "failsEarlyWhenThresholdUnreachable", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "failsEarlyWhenThresholdUnreachable", BlockType.VERDICT),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "failsEarlyWhenThresholdUnreachableTransparent", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
    Section(
        number = 3,
        heading = "Fail",
        intro = "A failing verdict. The verbose variant adds the HYPOTHESIS TEST and " +
                "STATISTICAL INFERENCE workings.",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.CodeBlock("SUMMARY", "serviceFailsNarrowlyTransparent", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "serviceFailsNarrowlyTransparent", BlockType.STATISTICAL_ANALYSIS),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "serviceFailsNarrowlyTransparent", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
    Section(
        number = 4,
        heading = "Budget exhaustion",
        intro = "When a cost budget (time or tokens) runs out before all samples complete, " +
                "PUnit reports how many samples were executed and the pass rate at termination. " +
                "The default budget-exhaustion behaviour is FAIL — the test fails regardless of " +
                "the partial results.",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.CodeBlock("SUMMARY", "failsWhenBudgetRunsOut", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "failsWhenBudgetRunsOut", BlockType.VERDICT),
            Part.Prose(
                "An alternative budget behaviour, `EVALUATE_PARTIAL`, evaluates the partial " +
                        "results against the threshold as if the test had completed normally. " +
                        "This can produce either a pass or fail depending on the partial results."
            ),
            Part.CodeBlock("SUMMARY", "evaluatesPartialResultsOnBudgetPass", BlockType.VERDICT),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "failsWhenBudgetRunsOutTransparent", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
    Section(
        number = 5,
        heading = "SLA compliance with contract provenance",
        intro = "When a threshold originates from an SLA, SLO, or policy, PUnit frames the " +
                "hypothesis test accordingly and includes a THRESHOLD PROVENANCE section tracing " +
                "the threshold back to its source. The hypothesis text adapts to the threshold " +
                "origin (e.g. \"system meets SLA requirement\" vs \"system meets SLO target\" vs " +
                "\"system meets policy requirement\").",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.CodeBlock("SUMMARY", "slaPassShowsComplianceHypothesis", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "slaPassShowsComplianceHypothesis", BlockType.STATISTICAL_ANALYSIS),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "slaPassShowsComplianceHypothesis", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
    Section(
        number = 6,
        heading = "Compliance undersized",
        intro = "When the sample size is too small to provide meaningful statistical evidence of " +
                "compliance with a high-reliability SLA target, PUnit warns that a passing result " +
                "is only a smoke-test-level observation. A failing result remains a reliable " +
                "indication of non-conformance.",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.CodeBlock("SUMMARY", "complianceUndersizedSmokeTestOnly", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "complianceUndersizedSmokeTestOnly", BlockType.STATISTICAL_ANALYSIS),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "complianceUndersizedSmokeTestOnly", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
    Section(
        number = 7,
        heading = "Covariate misalignment",
        intro = "When the test runs under conditions that differ from the baseline (e.g. " +
                "different time of day, weekday vs weekend), PUnit emits a BASELINE FOUND banner " +
                "listing the misaligned covariates before the test runs. This also appears as a " +
                "caveat in the verdict.",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.CodeBlock("SUMMARY", "temporalMismatchShowsCaveatTransparent", BlockType.BASELINE_FOUND),
            Part.CodeBlock("SUMMARY", "temporalMismatchShowsCaveatTransparent", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "temporalMismatchShowsCaveatTransparent", BlockType.STATISTICAL_ANALYSIS),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "temporalMismatchShowsCaveatTransparent", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
    Section(
        number = 8,
        heading = "Intent-governed verdicts (VERIFICATION vs SMOKE)",
        intro = "PUnit supports two test intents: **VERIFICATION** (evidential — enforces a " +
                "statistical feasibility gate before any samples execute) and **SMOKE** (sentinel — " +
                "permits undersized configurations and uses softened language). The intent appears " +
                "in the verdict banner and influences hypothesis framing and caveats.\n\n" +
                "A VERIFICATION test with insufficient samples will fail at configuration time " +
                "(not shown here — see the user guide for the infeasibility message). The examples " +
                "below show how intent affects the verdict for tests that *do* execute.",
        parts = listOf(
            Part.Subheading("Summary"),
            Part.Subheading("Verification (sized, normative)"),
            Part.CodeBlock("SUMMARY", "verificationPassSized", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "verificationPassSized", BlockType.STATISTICAL_ANALYSIS),
            Part.Prose(
                "When a test is configured with `intent = SMOKE` and a normative threshold origin " +
                        "(SLA, SLO, or POLICY), PUnit uses softened language — \"consistent with the " +
                        "target\" rather than \"meets SLA requirement\". If the sample is undersized " +
                        "for verification, a sizing note appears."
            ),
            Part.Subheading("Smoke undersized (normative)"),
            Part.CodeBlock("SUMMARY", "smokeUndersizedNormative", BlockType.TEST_CONFIGURATION),
            Part.CodeBlock("SUMMARY", "smokeUndersizedNormative", BlockType.STATISTICAL_ANALYSIS),
            Part.Prose(
                "If the SMOKE sample is already large enough for verification, PUnit suggests " +
                        "upgrading to `intent = VERIFICATION` for evidential strength."
            ),
            Part.Subheading("Smoke sized with hint (normative)"),
            Part.CodeBlock("SUMMARY", "smokeSizedNormativeHint", BlockType.STATISTICAL_ANALYSIS),
            Part.Subheading("Verbose"),
            Part.CodeBlock("VERBOSE", "verificationPassSized", BlockType.STATISTICAL_ANALYSIS),
            Part.CodeBlock("VERBOSE", "smokeUndersizedNormative", BlockType.STATISTICAL_ANALYSIS),
        ),
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Document assembly
// ─────────────────────────────────────────────────────────────────────────────

private fun assemble(summaryIndex: BlockIndex, verboseIndex: BlockIndex): String {
    val sb = StringBuilder()

    // Document header
    sb.appendLine("# Verdict Catalog")
    sb.appendLine()
    sb.appendLine("A curated collection of archetypal PUnit verdicts, organized from simplest to most complex.")
    sb.appendLine()
    sb.appendLine("PUnit verdicts are available at two detail levels:")
    sb.appendLine()
    sb.appendLine("- **Summary** (default) — compact verdict with pass rate comparison, termination reason, and caveats")
    sb.appendLine("- **Verbose** (`-Dpunit.stats.detail=VERBOSE`) — full statistical analysis including hypothesis test formulation, confidence intervals, and inference workings")
    sb.appendLine()
    sb.appendLine("The numerical values shown below come from actual test runs and will vary between executions due to the probabilistic nature of the tests.")

    for (section in sections()) {
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## ${section.number}. ${section.heading}")
        sb.appendLine()
        sb.appendLine(section.intro)

        for (part in section.parts) {
            when (part) {
                is Part.Subheading -> {
                    sb.appendLine()
                    sb.appendLine("### ${part.text}")
                }
                is Part.CodeBlock -> {
                    val index = if (part.level == "SUMMARY") summaryIndex else verboseIndex
                    val block = index.get(part.method, part.type)
                    sb.appendLine()
                    sb.appendLine(fenced(block))
                }
                is Part.Prose -> {
                    sb.appendLine()
                    sb.appendLine(part.text)
                }
            }
        }
    }

    sb.appendLine()

    return sb.toString()
}

// ─────────────────────────────────────────────────────────────────────────────
// Main
// ─────────────────────────────────────────────────────────────────────────────

fun main() {
    val summaryFile = File("build/verdict-catalogue-SUMMARY.md")
    val verboseFile = File("build/verdict-catalogue-VERBOSE.md")

    require(summaryFile.exists()) {
        "Summary catalogue not found: ${summaryFile.absolutePath}\n" +
                "Run: ./gradlew verdictCatalogueSummary"
    }
    require(verboseFile.exists()) {
        "Verbose catalogue not found: ${verboseFile.absolutePath}\n" +
                "Run: ./gradlew verdictCatalogueVerbose"
    }

    val summaryBlocks = parseBlocks(summaryFile)
    val verboseBlocks = parseBlocks(verboseFile)

    println("Parsed ${summaryBlocks.size} blocks from SUMMARY catalogue")
    println("Parsed ${verboseBlocks.size} blocks from VERBOSE catalogue")

    val summaryIndex = BlockIndex(summaryBlocks)
    val verboseIndex = BlockIndex(verboseBlocks)

    val output = assemble(summaryIndex, verboseIndex)

    val outputFile = File("docs/VERDICT-CATALOG.md")
    outputFile.parentFile.mkdirs()
    outputFile.writeText(output)

    println("Generated: ${outputFile.path}")
}
