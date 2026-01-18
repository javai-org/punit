package org.javai.punit.experiment.optimize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Generates optimization history YAML files for @OptimizeExperiment.
 *
 * <p>Output directory: {@code src/test/resources/punit/optimizations/{useCaseId}/}
 * <p>Filename pattern: {@code {experimentId}_YYYYMMDD_HHMMSS.yaml}
 *
 * <p>The YAML output includes:
 * <ul>
 *   <li>Identification (useCaseId, experimentId)</li>
 *   <li>Treatment factor configuration</li>
 *   <li>Fixed factors</li>
 *   <li>Optimization settings (objective, scorer, mutator, termination policy)</li>
 *   <li>Timing information</li>
 *   <li>All iterations with their factor values, statistics, and scores</li>
 *   <li>Best iteration identified</li>
 *   <li>Termination reason</li>
 *   <li>Summary statistics (score improvement)</li>
 * </ul>
 */
public class OptimizeSpecGenerator {

    private static final String DEFAULT_OUTPUT_DIR = "src/test/resources/punit/optimizations";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String SCHEMA_VERSION = "punit-optimize-1";

    /**
     * Formats a score (0.0-1.0) as a percentage with 1 decimal place.
     * Example: 0.88 → "88.0%"
     */
    private static String formatAsPercent(double score) {
        return String.format("%.1f%%", score * 100);
    }

    /**
     * Generates the optimization history YAML file.
     *
     * @param context the extension context for publishing reports
     * @param history the complete optimization history
     */
    public void generateSpec(ExtensionContext context, OptimizeHistory history) {
        try {
            Path outputPath = resolveOutputPath(history);
            writeYaml(history, outputPath);

            context.publishReportEntry("punit.optimization.outputPath", outputPath.toString());
            publishFinalReport(context, history);
        } catch (IOException e) {
            context.publishReportEntry("punit.optimization.error", e.getMessage());
        }
    }

    private Path resolveOutputPath(OptimizeHistory history) throws IOException {
        String outputDirOverride = System.getProperty("punit.optimizations.outputDir");
        Path baseDir;
        if (outputDirOverride != null && !outputDirOverride.isEmpty()) {
            baseDir = Paths.get(outputDirOverride);
        } else {
            baseDir = Paths.get(DEFAULT_OUTPUT_DIR);
        }

        // Create subdirectory for the use case
        Path useCaseDir = baseDir.resolve(sanitizeForFilename(history.useCaseId()));
        Files.createDirectories(useCaseDir);

        // Generate filename with timestamp
        String experimentId = history.experimentId();
        if (experimentId == null || experimentId.isEmpty()) {
            experimentId = "optimization";
        }
        String timestamp = TIMESTAMP_FORMAT.format(history.startTime());
        String filename = sanitizeForFilename(experimentId) + "_" + timestamp + ".yaml";

        return useCaseDir.resolve(filename);
    }

    private void writeYaml(OptimizeHistory history, Path outputPath) throws IOException {
        String content = toYaml(history);
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
    }

    /**
     * Converts optimization history to YAML format.
     *
     * @param history the optimization history
     * @return YAML string
     */
    public String toYaml(OptimizeHistory history) {
        String contentWithoutFingerprint = buildYamlContent(history);
        String fingerprint = computeFingerprint(contentWithoutFingerprint);

        StringBuilder sb = new StringBuilder(contentWithoutFingerprint);
        sb.append("contentFingerprint: ").append(fingerprint).append("\n");

        return sb.toString();
    }

    private String buildYamlContent(OptimizeHistory history) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# Optimization History for ").append(history.useCaseId()).append("\n");
        sb.append("# Primary output: the best value for the control factor\n");
        sb.append("# Generated automatically by punit @OptimizeExperiment\n\n");

        sb.append("schemaVersion: ").append(SCHEMA_VERSION).append("\n");

        // Identification
        sb.append("useCaseId: ").append(history.useCaseId()).append("\n");
        if (history.experimentId() != null && !history.experimentId().isEmpty()) {
            sb.append("experimentId: ").append(history.experimentId()).append("\n");
        }

        // Treatment factor
        sb.append("\ncontrolFactor:\n");
        sb.append("  name: ").append(history.controlFactorName()).append("\n");
        if (history.controlFactorType() != null) {
            sb.append("  type: ").append(history.controlFactorType()).append("\n");
        }

        // Fixed factors
        if (history.fixedFactors() != null && history.fixedFactors().size() > 0) {
            sb.append("\nfixedFactors:\n");
            for (Map.Entry<String, Object> entry : history.fixedFactors().asMap().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ");
                appendYamlValue(sb, entry.getValue());
                sb.append("\n");
            }
        }

        // Optimization settings
        sb.append("\noptimization:\n");
        sb.append("  objective: ").append(history.objective().name()).append("\n");
        sb.append("  scorer: ").append(quoteIfNeeded(history.scorerDescription())).append("\n");
        sb.append("  mutator: ").append(quoteIfNeeded(history.mutatorDescription())).append("\n");
        sb.append("  terminationPolicy: ").append(quoteIfNeeded(history.terminationPolicyDescription())).append("\n");

        // Timing
        sb.append("\ntiming:\n");
        if (history.startTime() != null) {
            sb.append("  startTime: ").append(ISO_FORMATTER.format(history.startTime())).append("\n");
        }
        if (history.endTime() != null) {
            sb.append("  endTime: ").append(ISO_FORMATTER.format(history.endTime())).append("\n");
        }
        sb.append("  totalDurationMs: ").append(history.totalDuration().toMillis()).append("\n");

        // Best iteration (highlighted early for easy access)
        Optional<OptimizationRecord> bestOpt = history.bestIteration();
        if (bestOpt.isPresent()) {
            OptimizationRecord best = bestOpt.get();
            sb.append("\nbestIteration:\n");
            sb.append("  iterationNumber: ").append(best.aggregate().iterationNumber()).append("\n");
            sb.append("  score: ").append(formatAsPercent(best.score())).append("\n");

            // Emphasize the best treatment value
            Object bestValue = best.aggregate().controlFactorValue();
            sb.append("  bestControlFactor: ");
            if (bestValue instanceof String s && s.contains("\n")) {
                sb.append("|\n");
                for (String line : s.split("\n")) {
                    sb.append("    ").append(line).append("\n");
                }
            } else {
                appendYamlValue(sb, bestValue);
                sb.append("\n");
            }

            OptimizeStatistics stats = best.aggregate().statistics();
            sb.append("  statistics:\n");
            sb.append("    sampleCount: ").append(stats.sampleCount()).append("\n");
            sb.append("    successRate: ").append(formatAsPercent(stats.successRate())).append("\n");
            sb.append("    totalTokens: ").append(stats.totalTokens()).append("\n");
        }

        // Summary
        sb.append("\nsummary:\n");
        sb.append("  totalIterations: ").append(history.iterationCount()).append("\n");
        sb.append("  totalTokens: ").append(history.totalTokens()).append("\n");

        history.initialScore().ifPresent(score ->
                sb.append("  initialScore: ").append(formatAsPercent(score)).append("\n"));
        history.bestScore().ifPresent(score ->
                sb.append("  bestScore: ").append(formatAsPercent(score)).append("\n"));

        sb.append("  scoreImprovement: ").append(formatAsPercent(history.scoreImprovement())).append("\n");

        // Termination reason
        if (history.terminationReason() != null) {
            sb.append("\nterminationReason:\n");
            sb.append("  cause: ").append(history.terminationReason().cause().name()).append("\n");
            sb.append("  message: ").append(quoteIfNeeded(history.terminationReason().message())).append("\n");
        }

        // All iterations
        sb.append("\niterations:\n");
        for (OptimizationRecord record : history.iterations()) {
            appendIteration(sb, record);
        }

        return sb.toString();
    }

    private void appendIteration(StringBuilder sb, OptimizationRecord record) {
        OptimizationIterationAggregate agg = record.aggregate();

        sb.append("  - iterationNumber: ").append(agg.iterationNumber()).append("\n");
        sb.append("    status: ").append(record.status().name()).append("\n");
        sb.append("    score: ").append(formatAsPercent(record.score())).append("\n");

        // Control factor value for this iteration
        Object controlFactorValue = agg.controlFactorValue();
        sb.append("    controlFactor: ");
        if (controlFactorValue instanceof String s && s.contains("\n")) {
            // Use YAML block scalar for multiline strings
            sb.append("|\n");
            for (String line : s.split("\n")) {
                sb.append("      ").append(line).append("\n");
            }
        } else {
            appendYamlValue(sb, controlFactorValue);
            sb.append("\n");
        }

        // Statistics
        OptimizeStatistics stats = agg.statistics();
        sb.append("    statistics:\n");
        sb.append("      sampleCount: ").append(stats.sampleCount()).append("\n");
        sb.append("      successRate: ").append(formatAsPercent(stats.successRate())).append("\n");
        sb.append("      successCount: ").append(stats.successCount()).append("\n");
        sb.append("      failureCount: ").append(stats.failureCount()).append("\n");
        sb.append("      totalTokens: ").append(stats.totalTokens()).append("\n");
        sb.append("      meanLatencyMs: ").append(String.format("%.2f", stats.meanLatencyMs())).append("\n");

        record.failureReason().ifPresent(reason ->
                sb.append("    failureReason: ").append(quoteIfNeeded(reason)).append("\n"));
    }

    private String computeFingerprint(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void appendYamlValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            if (needsYamlQuoting(str)) {
                sb.append("\"").append(escapeYamlString(str)).append("\"");
            } else {
                sb.append(str);
            }
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else {
            sb.append("\"").append(escapeYamlString(value.toString())).append("\"");
        }
    }

    private String quoteIfNeeded(String str) {
        if (str == null) return "null";
        if (needsYamlQuoting(str)) {
            return "\"" + escapeYamlString(str) + "\"";
        }
        return str;
    }

    private boolean needsYamlQuoting(String str) {
        if (str == null || str.isEmpty()) return true;
        if (str.contains(":") || str.contains("#") || str.contains("\"") ||
                str.contains("'") || str.contains("\n") || str.contains("\r") ||
                str.contains("[") || str.contains("]") || str.contains("{") ||
                str.contains("}")) {
            return true;
        }
        // Check for YAML special values
        String lower = str.toLowerCase();
        return lower.equals("true") || lower.equals("false") ||
                lower.equals("null") || lower.equals("yes") || lower.equals("no");
    }

    private String escapeYamlString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void publishFinalReport(ExtensionContext context, OptimizeHistory history) {
        context.publishReportEntry("punit.experiment.complete", "true");
        context.publishReportEntry("punit.mode", "OPTIMIZE");
        context.publishReportEntry("punit.useCaseId", history.useCaseId());
        context.publishReportEntry("punit.controlFactor", history.controlFactorName());
        context.publishReportEntry("punit.iterationsCompleted",
                String.valueOf(history.iterationCount()));

        history.bestScore().ifPresent(score ->
                context.publishReportEntry("punit.bestScore", formatAsPercent(score)));

        history.initialScore().ifPresent(score ->
                context.publishReportEntry("punit.initialScore", formatAsPercent(score)));

        context.publishReportEntry("punit.scoreImprovement", formatAsPercent(history.scoreImprovement()));

        if (history.terminationReason() != null) {
            context.publishReportEntry("punit.terminationReason",
                    history.terminationReason().cause().name());
        }

        context.publishReportEntry("punit.totalDurationMs",
                String.valueOf(history.totalDuration().toMillis()));
        context.publishReportEntry("punit.totalTokens",
                String.valueOf(history.totalTokens()));

        // Highlight the best factor value
        history.bestFactorValue().ifPresent(value -> {
            String valueStr = value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
            if (valueStr.length() > 100) {
                valueStr = valueStr.substring(0, 100) + "...";
            }
            context.publishReportEntry("punit.bestFactorValue", valueStr);
        });
    }

    private String sanitizeForFilename(String input) {
        if (input == null) return "unnamed";
        return input.replaceAll("[^a-zA-Z0-9_-]", "-");
    }
}
