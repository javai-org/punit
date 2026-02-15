package org.javai.punit.experiment.explore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.javai.punit.experiment.engine.YamlBuilder;
import org.javai.punit.experiment.engine.output.DescriptiveStatistics;
import org.javai.punit.experiment.engine.output.DiffAnchorGenerator;
import org.javai.punit.experiment.engine.output.OutputUtilities;
import org.javai.punit.experiment.engine.output.OutputUtilities.OutputHeader;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.ResultProjection;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.ExpirationPolicy;

/**
 * Writes exploration output for @ExploreExperiment.
 *
 * <p>Explore output is designed for <b>comparative discovery</b> with small samples
 * (~20 per configuration). The output emphasizes what was observed without the
 * misleading precision of inferential statistics.
 *
 * <h2>Key Differences from MEASURE Output</h2>
 * <ul>
 *   <li><b>No requirements section</b> - exploration is comparative, not prescriptive</li>
 *   <li><b>No standard error</b> - unreliable with small samples</li>
 *   <li><b>No confidence interval</b> - wide intervals state the obvious</li>
 *   <li><b>Descriptive statistics only</b> - observed rate, counts, failure distribution</li>
 * </ul>
 *
 * <h2>Output Structure</h2>
 * <pre>
 * schemaVersion: punit-spec-1
 * useCaseId: ...
 * execution: ...
 * statistics:
 *   observed: 0.7000     # What we saw
 *   successes: 14        # Raw counts
 *   failures: 6
 *   failureDistribution: # Qualitative insight
 *     TIMEOUT: 3
 * cost: ...
 * resultProjection: ...
 * </pre>
 *
 * @see org.javai.punit.experiment.measure.MeasureOutputWriter
 */
public class ExploreOutputWriter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Writes an exploration baseline to the specified path in YAML format.
     *
     * @param baseline the baseline to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public void write(EmpiricalBaseline baseline, Path path) throws IOException {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(path, "path must not be null");

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String content = toYaml(baseline);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Converts a baseline to YAML format for exploration output.
     *
     * @param baseline the baseline
     * @return YAML string
     */
    public String toYaml(EmpiricalBaseline baseline) {
        String contentWithoutFingerprint = buildYamlContent(baseline);
        return OutputUtilities.appendFingerprint(contentWithoutFingerprint);
    }

    private String buildYamlContent(EmpiricalBaseline baseline) {
        YamlBuilder builder = YamlBuilder.create();

        writeHeader(builder, baseline);
        writeCovariates(builder, baseline);
        writeExecution(builder, baseline);
        // NO requirements section - exploration is comparative, not prescriptive
        writeStatistics(builder, baseline);
        writeCost(builder, baseline);
        writeSuccessCriteria(builder, baseline);
        writeResultProjections(builder, baseline);
        writeExpiration(builder, baseline);

        return builder.build();
    }

    private void writeHeader(YamlBuilder builder, EmpiricalBaseline baseline) {
        OutputHeader header = OutputHeader.forBaseline(
            baseline.getUseCaseId(),
            baseline.getExperimentId(),
            baseline.getGeneratedAt(),
            baseline.getExperimentClass(),
            baseline.getExperimentMethod()
        );
        OutputUtilities.writeHeader(builder, header);

        if (baseline.hasFootprint()) {
            builder.field("footprint", baseline.getFootprint());
        }
    }

    private void writeCovariates(YamlBuilder builder, EmpiricalBaseline baseline) {
        if (!baseline.hasCovariates()) {
            return;
        }
        builder.startObject("covariates");
        CovariateProfile profile = baseline.getCovariateProfile();
        for (String key : profile.orderedKeys()) {
            CovariateValue value = profile.get(key);
            builder.field(key, value.toCanonicalString());
        }
        builder.endObject();
    }

    private void writeExecution(YamlBuilder builder, EmpiricalBaseline baseline) {
        builder.startObject("execution")
            .field("samplesPlanned", baseline.getExecution().samplesPlanned())
            .field("samplesExecuted", baseline.getExecution().samplesExecuted())
            .field("terminationReason", baseline.getExecution().terminationReason())
            .fieldIfPresent("terminationDetails", baseline.getExecution().terminationDetails())
            .endObject();
    }

    /**
     * Writes descriptive statistics only - no SE, CI, or derived thresholds.
     */
    private void writeStatistics(YamlBuilder builder, EmpiricalBaseline baseline) {
        var stats = baseline.getStatistics();

        // Convert to descriptive statistics (drop inferential stats)
        DescriptiveStatistics descriptive = DescriptiveStatistics.of(
            stats.observedSuccessRate(),
            stats.successes(),
            stats.failures(),
            stats.failureDistribution()
        );

        descriptive.writeTo(builder);
    }

    private void writeCost(YamlBuilder builder, EmpiricalBaseline baseline) {
        builder.startObject("cost")
            .field("totalTimeMs", baseline.getCost().totalTimeMs())
            .field("avgTimePerSampleMs", baseline.getCost().avgTimePerSampleMs())
            .field("totalTokens", baseline.getCost().totalTokens())
            .field("avgTokensPerSample", baseline.getCost().avgTokensPerSample())
            .endObject();
    }

    private void writeSuccessCriteria(YamlBuilder builder, EmpiricalBaseline baseline) {
        if (baseline.getUseCaseCriteria() == null) {
            return;
        }
        builder.startObject("successCriteria")
            .field("definition", baseline.getUseCaseCriteria())
            .endObject();
    }

    private void writeResultProjections(YamlBuilder builder, EmpiricalBaseline baseline) {
        if (!baseline.hasResultProjections()) {
            return;
        }
        builder.startObject("resultProjection");
        for (ResultProjection projection : baseline.getResultProjections()) {
            String anchorLine = DiffAnchorGenerator.anchorLine(projection.sampleIndex());
            builder.rawLine(anchorLine);
            writeResultProjection(builder, projection);
        }
        builder.endObject();
    }

    private void writeResultProjection(YamlBuilder builder, ResultProjection projection) {
        String sampleKey = "sample[" + projection.sampleIndex() + "]";
        builder.startObject(sampleKey);

        if (projection.input() != null) {
            builder.field("input", projection.input());
        }

        if (!projection.postconditions().isEmpty()) {
            builder.startObject("postconditions");
            for (var entry : projection.postconditions().entrySet()) {
                builder.field(entry.getKey(), entry.getValue());
            }
            builder.endObject();
        }

        builder.field("executionTimeMs", projection.executionTimeMs());

        if (projection.content() != null && !projection.content().isEmpty()) {
            builder.blockScalar("content", projection.content());
        }

        if (projection.failureDetail() != null) {
            builder.field("failureDetail", projection.failureDetail());
        }

        builder.endObject();
    }

    private void writeExpiration(YamlBuilder builder, EmpiricalBaseline baseline) {
        if (!baseline.hasExpirationPolicy()) {
            return;
        }
        ExpirationPolicy policy = baseline.getExpirationPolicy();
        builder.startObject("expiration")
            .field("expiresInDays", policy.expiresInDays())
            .field("baselineEndTime", ISO_FORMATTER.format(policy.baselineEndTime()));
        policy.expirationTime().ifPresent(exp ->
            builder.field("expirationDate", ISO_FORMATTER.format(exp)));
        builder.endObject();
    }
}
