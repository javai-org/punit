package org.javai.punit.experiment.explore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.javai.punit.api.UseCaseContext;
import org.javai.punit.experiment.engine.EmpiricalBaselineGenerator;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Generates spec files for @ExploreExperiment configurations.
 *
 * <p>Output structure:
 * <pre>
 * build/punit/explorations/
 * └── {UseCaseId}/
 *     ├── model-gpt-4_temp-0.0.yaml
 *     └── model-gpt-4_temp-0.7.yaml
 * </pre>
 *
 * <p>Explore results are human-review artefacts with no downstream programmatic
 * consumer, so they default to {@code build/} as transient build output rather
 * than being tracked in source. Override with the
 * {@code punit.explorations.outputDir} system property (the Gradle plugin
 * forwards the {@code punit.explorationsDir} extension value).
 */
public class ExploreSpecGenerator {

    private static final String DEFAULT_EXPLORATIONS_DIR = "build/punit/explorations";

    /**
     * Generates a single spec file for the use case (no per-config breakdown).
     *
     * <p>Used when @InputSource is present with round-robin cycling — all inputs
     * contribute to a single aggregated exploration spec.
     */
    public void generateSpec(
            ExtensionContext context,
            ExtensionContext.Store store,
            ExperimentResultAggregator aggregator) {

        ExploreConfig config = (ExploreConfig) store.get("config", ExperimentConfig.class);
        String useCaseId = config.useCaseId();
        int expiresInDays = config.expiresInDays();

        UseCaseContext useCaseContext = DefaultUseCaseContext.builder().build();

        EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = generator.generate(
                aggregator,
                context.getTestClass().orElse(null),
                context.getTestMethod().orElse(null),
                useCaseContext,
                expiresInDays
        );

        try {
            String filename = useCaseId.replace('.', '-') + ".yaml";
            String outputDirOverride = System.getProperty("punit.explorations.outputDir");
            Path baseDir = (outputDirOverride != null && !outputDirOverride.isEmpty())
                    ? Paths.get(outputDirOverride) : Paths.get(DEFAULT_EXPLORATIONS_DIR);
            Files.createDirectories(baseDir);
            Path outputPath = baseDir.resolve(filename);

            ExploreOutputWriter writer = new ExploreOutputWriter();
            writer.write(baseline, outputPath);

            context.publishReportEntry("punit.spec.outputPath", outputPath.toString());
        } catch (IOException e) {
            context.publishReportEntry("punit.spec.error", e.getMessage());
        }

        context.publishReportEntry("punit.config.successRate",
                String.format("%.4f", aggregator.getObservedSuccessRate()));
    }

    /**
     * Generates a spec file for a single configuration.
     */
    public void generateSpec(
            ExtensionContext context,
            ExtensionContext.Store store,
            String configName,
            ExperimentResultAggregator aggregator) {

        ExploreConfig config = (ExploreConfig) store.get("config", ExperimentConfig.class);
        String useCaseId = config.useCaseId();
        int expiresInDays = config.expiresInDays();

        UseCaseContext useCaseContext = DefaultUseCaseContext.builder().build();

        EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = generator.generate(
                aggregator,
                context.getTestClass().orElse(null),
                context.getTestMethod().orElse(null),
                useCaseContext,
                expiresInDays
        );

        // Write spec to config-specific file using explore-specific output format
        try {
            Path outputPath = resolveOutputPath(useCaseId, configName);
            ExploreOutputWriter writer = new ExploreOutputWriter();
            writer.write(baseline, outputPath);

            context.publishReportEntry("punit.spec.outputPath", outputPath.toString());
            context.publishReportEntry("punit.config.complete", configName);
        } catch (IOException e) {
            context.publishReportEntry("punit.spec.error",
                    "Config " + configName + ": " + e.getMessage());
        }

        // Publish report for this config
        context.publishReportEntry("punit.config.successRate",
                String.format("%s: %.4f", configName, aggregator.getObservedSuccessRate()));
    }

    private Path resolveOutputPath(String useCaseId, String configName) throws IOException {
        String filename = configName + ".yaml";

        String outputDirOverride = System.getProperty("punit.explorations.outputDir");
        Path baseDir;
        if (outputDirOverride != null && !outputDirOverride.isEmpty()) {
            baseDir = Paths.get(outputDirOverride);
        } else {
            baseDir = Paths.get(DEFAULT_EXPLORATIONS_DIR);
        }

        // Create subdirectory for use case
        Path useCaseDir = baseDir.resolve(useCaseId.replace('.', '-'));
        Files.createDirectories(useCaseDir);

        return useCaseDir.resolve(filename);
    }
}
