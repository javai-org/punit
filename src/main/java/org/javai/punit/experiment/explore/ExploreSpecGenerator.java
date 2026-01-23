package org.javai.punit.experiment.explore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.javai.punit.api.UseCaseContext;
import org.javai.punit.experiment.engine.BaselineWriter;
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
 * src/test/resources/punit/explorations/
 * └── {UseCaseId}/
 *     ├── model-gpt-4_temp-0.0.yaml
 *     └── model-gpt-4_temp-0.7.yaml
 * </pre>
 */
public class ExploreSpecGenerator {

    private static final String DEFAULT_EXPLORATIONS_DIR = "src/test/resources/punit/explorations";

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

        // Write spec to config-specific file
        try {
            Path outputPath = resolveOutputPath(useCaseId, configName);
            BaselineWriter writer = new BaselineWriter();
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
