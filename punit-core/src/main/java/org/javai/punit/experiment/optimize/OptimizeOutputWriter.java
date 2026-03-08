package org.javai.punit.experiment.optimize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.punit.experiment.engine.YamlBuilder;
import org.javai.punit.experiment.engine.output.DescriptiveStatistics;
import org.javai.punit.experiment.engine.output.OutputUtilities;
import org.javai.punit.experiment.engine.output.OutputUtilities.OutputHeader;

/**
 * Writes optimization output for @OptimizeExperiment.
 *
 * <p>Optimize output captures the <b>search trajectory</b> across iterations,
 * each with small samples (~20). Like EXPLORE, it uses descriptive statistics
 * without misleading inferential measures.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Best iteration highlight</b> - optimal value found during search</li>
 *   <li><b>Iteration history</b> - complete trajectory for analysis</li>
 *   <li><b>Descriptive statistics</b> - observed rate, counts per iteration</li>
 *   <li><b>No inferential stats</b> - unreliable with ~20 samples per iteration</li>
 * </ul>
 *
 * <h2>Output Structure</h2>
 * <pre>
 * schemaVersion: punit-optimize-1
 * useCaseId: ...
 * controlFactor: ...
 * optimization: ...
 * bestIteration:
 *   iterationNumber: 3
 *   score: 88.0%
 *   statistics:
 *     observed: 0.8800
 *     successes: 17
 *     failures: 3
 * iterations:
 *   - iterationNumber: 1
 *     statistics: ...
 * </pre>
 *
 * @see org.javai.punit.experiment.measure.MeasureOutputWriter
 * @see org.javai.punit.experiment.explore.ExploreOutputWriter
 */
public class OptimizeOutputWriter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Writes optimization history to the specified path in YAML format.
     *
     * @param history the optimization history to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public void write(OptimizeHistory history, Path path) throws IOException {
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(path, "path must not be null");

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String content = toYaml(history);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Converts optimization history to YAML format.
     *
     * @param history the optimization history
     * @return YAML string
     */
    public String toYaml(OptimizeHistory history) {
        String contentWithoutFingerprint = buildYamlContent(history);
        return OutputUtilities.appendFingerprint(contentWithoutFingerprint);
    }

    private String buildYamlContent(OptimizeHistory history) {
        YamlBuilder builder = YamlBuilder.create();

        writeHeader(builder, history);
        writeControlFactor(builder, history);
        writeFixedFactors(builder, history);
        writeOptimizationSettings(builder, history);
        writeTiming(builder, history);
        writeBestIteration(builder, history);
        writeSummary(builder, history);
        writeTerminationReason(builder, history);
        writeIterations(builder, history);

        return builder.build();
    }

    private void writeHeader(YamlBuilder builder, OptimizeHistory history) {
        OutputHeader header = OutputHeader.forOptimization(
            history.useCaseId(),
            history.experimentId(),
            history.startTime()
        );
        OutputUtilities.writeHeader(builder, header);
    }

    private void writeControlFactor(YamlBuilder builder, OptimizeHistory history) {
        builder.startObject("controlFactor")
            .field("name", history.controlFactorName())
            .fieldIfPresent("type", history.controlFactorType())
            .endObject();
    }

    private void writeFixedFactors(YamlBuilder builder, OptimizeHistory history) {
        if (history.fixedFactors() == null || history.fixedFactors().size() == 0) {
            return;
        }
        builder.startObject("fixedFactors");
        for (Map.Entry<String, Object> entry : history.fixedFactors().asMap().entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();
    }

    private void writeOptimizationSettings(YamlBuilder builder, OptimizeHistory history) {
        builder.startObject("optimization")
            .field("objective", history.objective().name())
            .field("scorer", history.scorerDescription())
            .field("mutator", history.mutatorDescription())
            .field("terminationPolicy", history.terminationPolicyDescription())
            .endObject();
    }

    private void writeTiming(YamlBuilder builder, OptimizeHistory history) {
        builder.startObject("timing");
        if (history.startTime() != null) {
            builder.field("startTime", ISO_FORMATTER.format(history.startTime()));
        }
        if (history.endTime() != null) {
            builder.field("endTime", ISO_FORMATTER.format(history.endTime()));
        }
        builder.field("totalDurationMs", history.totalDuration().toMillis())
            .endObject();
    }

    private void writeBestIteration(YamlBuilder builder, OptimizeHistory history) {
        Optional<OptimizationRecord> bestOpt = history.bestIteration();
        if (bestOpt.isEmpty()) {
            return;
        }

        OptimizationRecord best = bestOpt.get();
        Object bestValue = best.aggregate().controlFactorValue();

        builder.startObject("bestIteration")
            .field("iterationNumber", best.aggregate().iterationNumber())
            .field("score", formatAsPercent(best.score()));

        // Handle multiline control factor values
        if (bestValue instanceof String s && s.contains("\n")) {
            builder.blockScalar("bestControlFactor", s);
        } else {
            builder.field("bestControlFactor", bestValue);
        }

        // Write descriptive statistics for best iteration
        OptimizeStatistics stats = best.aggregate().statistics();
        DescriptiveStatistics descriptive = DescriptiveStatistics.of(
            stats.successRate(),
            stats.successCount(),
            stats.failureCount()
        );
        descriptive.writeCompactTo(builder);

        builder.endObject();
    }

    private void writeSummary(YamlBuilder builder, OptimizeHistory history) {
        builder.startObject("summary")
            .field("totalIterations", history.iterationCount())
            .field("totalTokens", history.totalTokens());

        history.initialScore().ifPresent(score ->
            builder.field("initialScore", formatAsPercent(score)));
        history.bestScore().ifPresent(score ->
            builder.field("bestScore", formatAsPercent(score)));

        builder.field("scoreImprovement", formatAsPercent(history.scoreImprovement()))
            .endObject();
    }

    private void writeTerminationReason(YamlBuilder builder, OptimizeHistory history) {
        if (history.terminationReason() == null) {
            return;
        }
        builder.startObject("terminationReason")
            .field("cause", history.terminationReason().cause().name())
            .field("message", history.terminationReason().message())
            .endObject();
    }

    private void writeIterations(YamlBuilder builder, OptimizeHistory history) {
        builder.startList("iterations");
        for (OptimizationRecord record : history.iterations()) {
            writeIteration(builder, record);
        }
        builder.endList();
    }

    private void writeIteration(YamlBuilder builder, OptimizationRecord record) {
        OptimizationIterationAggregate agg = record.aggregate();
        Object controlFactorValue = agg.controlFactorValue();

        builder.startListItem()
            .field("iterationNumber", agg.iterationNumber())
            .field("status", record.status().name())
            .field("score", formatAsPercent(record.score()));

        // Handle multiline control factor values
        if (controlFactorValue instanceof String s && s.contains("\n")) {
            builder.blockScalar("controlFactor", s);
        } else {
            builder.field("controlFactor", controlFactorValue);
        }

        // Write descriptive statistics (no SE, CI)
        OptimizeStatistics stats = agg.statistics();
        DescriptiveStatistics descriptive = DescriptiveStatistics.of(
            stats.successRate(),
            stats.successCount(),
            stats.failureCount()
        );
        descriptive.writeCompactTo(builder);

        // Add additional iteration metrics
        builder.field("totalTokens", stats.totalTokens())
            .field("meanLatencyMs", stats.meanLatencyMs(), "%.2f");

        record.failureReason().ifPresent(reason ->
            builder.field("failureReason", reason));

        builder.endListItem();
    }

    /**
     * Formats a score (0.0-1.0) as a percentage with 1 decimal place.
     */
    private static String formatAsPercent(double score) {
        return String.format("%.1f%%", score * 100);
    }
}
