package org.javai.punit.experiment.optimize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.javai.punit.reporting.PUnitReporter;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Generates optimization history YAML files for @OptimizeExperiment.
 *
 * <p>Output directory: {@code src/test/resources/punit/optimizations/{useCaseId}/}
 * <p>Filename pattern: {@code {experimentId}_YYYYMMDD_HHMMSS.yaml}
 *
 * <p>Delegates YAML generation to {@link OptimizeOutputWriter}.
 */
public class OptimizeSpecGenerator {

    private static final String DEFAULT_OUTPUT_DIR = "src/test/resources/punit/optimizations";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    private final OptimizeOutputWriter writer = new OptimizeOutputWriter();
    private final PUnitReporter reporter = new PUnitReporter();

    /**
     * Generates the optimization history YAML file.
     *
     * @param context the extension context for publishing reports
     * @param history the complete optimization history
     */
    public void generateSpec(ExtensionContext context, OptimizeHistory history) {
        try {
            Path outputPath = resolveOutputPath(history);
            writer.write(history, outputPath);

            context.publishReportEntry("punit.optimization.outputPath", outputPath.toString());
            publishFinalReport(context, history);
            printCompletionSummary(history, outputPath);
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

    private void printCompletionSummary(OptimizeHistory history, Path outputPath) {
        String title = "OPTIMIZATION COMPLETE";

        StringBuilder sb = new StringBuilder();
        sb.append(history.useCaseId()).append("\n\n");

        // Summary line
        sb.append(PUnitReporter.labelValueLn("Iterations:", String.valueOf(history.iterationCount())));
        sb.append(PUnitReporter.labelValueLn("Control factor:", history.controlFactorName()));

        // Score progression
        history.initialScore().ifPresent(initial ->
                sb.append(PUnitReporter.labelValueLn("Initial score:", formatAsPercent(initial))));
        history.bestScore().ifPresent(best ->
                sb.append(PUnitReporter.labelValueLn("Best score:", formatAsPercent(best))));

        double improvement = history.scoreImprovement();
        if (improvement > 0) {
            sb.append(PUnitReporter.labelValueLn("Improvement:", "+" + formatAsPercent(improvement)));
        }

        // Termination reason
        if (history.terminationReason() != null) {
            sb.append(PUnitReporter.labelValueLn("Terminated:",
                    formatTerminationReason(history.terminationReason())));
        }

        // Duration and cost
        sb.append("\n");
        sb.append(PUnitReporter.labelValueLn("Duration:", formatDuration(history.totalDuration().toMillis())));
        sb.append(PUnitReporter.labelValueLn("Tokens used:", String.format("%,d", history.totalTokens())));

        // Output file
        sb.append("\n");
        sb.append(PUnitReporter.labelValueLn("Output:", outputPath.toString()));

        reporter.reportInfo(title, sb.toString());
    }

    private String formatTerminationReason(OptimizeTerminationReason reason) {
        // Use the message which contains more specific details
        return reason.message();
    }

    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    private String sanitizeForFilename(String input) {
        if (input == null) return "unnamed";
        return input.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    private static String formatAsPercent(double score) {
        return String.format("%.1f%%", score * 100);
    }
}
