package org.javai.punit.experiment.measure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.javai.punit.experiment.engine.YamlBuilder;
import org.javai.punit.experiment.engine.output.InferentialStatistics;
import org.javai.punit.experiment.engine.output.LatencySection;
import org.javai.punit.experiment.engine.output.OutputUtilities;
import org.javai.punit.experiment.engine.output.OutputUtilities.OutputHeader;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.ResultProjection;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.ExpirationPolicy;

/**
 * Writes measurement output for @MeasureExperiment.
 *
 * <p>Measure output is designed for <b>baseline establishment</b> with large samples
 * (1000+). The output includes full inferential statistics that are meaningful
 * with large sample sizes.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Requirements section</b> - derived minPassRate from CI lower bound</li>
 *   <li><b>Inferential statistics</b> - SE, CI meaningful with 1000+ samples</li>
 *   <li><b>Spec-driven test support</b> - output is consumed by SpecificationLoader</li>
 * </ul>
 *
 * <h2>Output Structure</h2>
 * <pre>
 * schemaVersion: punit-spec-1
 * useCaseId: ...
 * execution: ...
 * requirements:
 *   minPassRate: 0.8814  # Derived from CI lower bound
 * statistics:
 *   successRate:
 *     observed: 0.9000
 *     standardError: 0.0095
 *     confidenceInterval95: [0.8814, 0.9186]
 *   successes: 900
 *   failures: 100
 * cost: ...
 * resultProjection: ...
 * </pre>
 *
 * @see org.javai.punit.experiment.explore.ExploreOutputWriter
 */
public class MeasureOutputWriter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Writes a measurement baseline to the specified path in YAML format,
     * including all sections.
     *
     * @param baseline the baseline to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public void write(EmpiricalBaseline baseline, Path path) throws IOException {
        writeToFile(toYaml(baseline), path);
    }

    /**
     * Writes only the functional dimension (pass-rate statistics) to the specified path.
     * Latency data is excluded.
     *
     * @param baseline the baseline to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public void writeFunctional(EmpiricalBaseline baseline, Path path) throws IOException {
        writeToFile(toFunctionalYaml(baseline), path);
    }

    /**
     * Writes only the latency dimension to the specified path.
     * Pass-rate statistics and requirements are excluded.
     *
     * @param baseline the baseline to write
     * @param path the output path
     * @throws IOException if writing fails
     */
    public void writeLatency(EmpiricalBaseline baseline, Path path) throws IOException {
        writeToFile(toLatencyYaml(baseline), path);
    }

    private void writeToFile(String content, Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Converts a baseline to YAML format containing all sections.
     *
     * @param baseline the baseline
     * @return YAML string with both functional and latency data
     */
    public String toYaml(EmpiricalBaseline baseline) {
        return buildAndFingerprint(baseline, true, true);
    }

    /**
     * Converts a baseline to YAML format containing only functional data
     * (requirements, statistics). Latency data is excluded.
     *
     * @param baseline the baseline
     * @return YAML string with functional data only
     */
    public String toFunctionalYaml(EmpiricalBaseline baseline) {
        return buildAndFingerprint(baseline, true, false);
    }

    /**
     * Converts a baseline to YAML format containing only latency data.
     * Requirements and pass-rate statistics are excluded.
     *
     * @param baseline the baseline
     * @return YAML string with latency data only
     */
    public String toLatencyYaml(EmpiricalBaseline baseline) {
        return buildAndFingerprint(baseline, false, true);
    }

    private String buildAndFingerprint(EmpiricalBaseline baseline,
                                        boolean includeFunctional, boolean includeLatency) {
        String contentWithoutFingerprint = buildYamlContent(baseline, includeFunctional, includeLatency);
        return OutputUtilities.appendFingerprint(contentWithoutFingerprint);
    }

    private String buildYamlContent(EmpiricalBaseline baseline,
                                     boolean includeFunctional, boolean includeLatency) {
        YamlBuilder builder = YamlBuilder.create();

        writeHeader(builder, baseline);
        writeCovariates(builder, baseline);
        writeExecution(builder, baseline);
        if (includeFunctional) {
            writeRequirementsAndStatistics(builder, baseline);
        }
        if (includeLatency) {
            writeLatency(builder, baseline);
        }
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
        builder.startObject("execution");
        if (baseline.getExecution().warmup() > 0) {
            builder.field("warmup", baseline.getExecution().warmup());
        }
        builder.field("samplesPlanned", baseline.getExecution().samplesPlanned())
            .field("samplesExecuted", baseline.getExecution().samplesExecuted())
            .field("terminationReason", baseline.getExecution().terminationReason())
            .fieldIfPresent("terminationDetails", baseline.getExecution().terminationDetails())
            .endObject();
    }

    /**
     * Writes both requirements and full inferential statistics.
     */
    private void writeRequirementsAndStatistics(YamlBuilder builder, EmpiricalBaseline baseline) {
        var stats = baseline.getStatistics();

        // Convert to inferential statistics
        InferentialStatistics inferential = InferentialStatistics.of(
            stats.observedSuccessRate(),
            stats.standardError(),
            stats.confidenceIntervalLower(),
            stats.confidenceIntervalUpper(),
            stats.successes(),
            stats.failures(),
            stats.failureDistribution()
        );

        // Write requirements section (derived from CI lower bound)
        inferential.writeRequirementsTo(builder);

        // Write full statistics section
        inferential.writeTo(builder);
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

    private void writeLatency(YamlBuilder builder, EmpiricalBaseline baseline) {
        LatencySection.writeTo(builder, baseline);
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
