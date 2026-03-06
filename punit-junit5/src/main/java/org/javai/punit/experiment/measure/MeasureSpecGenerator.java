package org.javai.punit.experiment.measure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import org.javai.punit.api.UseCaseContext;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.EmpiricalBaselineGenerator;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.spec.baseline.BaselineFileNamer;
import org.javai.punit.spec.baseline.FootprintComputer;
import org.javai.punit.spec.baseline.covariate.CovariateProfileResolver;
import org.javai.punit.spec.baseline.covariate.DefaultCovariateResolutionContext;
import org.javai.punit.spec.baseline.covariate.UseCaseCovariateExtractor;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Generates empirical spec files for @MeasureExperiment.
 *
 * <p>Output: {@code src/test/resources/punit/specs/{UseCaseName}-{footprint}[-{covHashes}].yaml}
 *
 * <p>Each use case should have ONE measure experiment that establishes the production
 * baseline. The probabilistic test then uses the same factor source, ensuring the
 * test cycles through the same values as the baseline measurement.
 */
public class MeasureSpecGenerator {

    private static final String DEFAULT_SPECS_DIR = "src/test/resources/punit/specs";

    /**
     * Generates the spec file for a completed MEASURE experiment.
     */
    public void generateSpec(ExtensionContext context, ExtensionContext.Store store) {
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        MeasureConfig config = (MeasureConfig) store.get("config", ExperimentConfig.class);
        Class<?> useCaseClass = config.useCaseClass();
        String useCaseId = aggregator.getUseCaseId();

        int expiresInDays = config.expiresInDays();

        // Emit informational note if no expiration is set
        if (expiresInDays == 0) {
            context.publishReportEntry("punit.info.expiration",
                    "Consider setting expiresInDays to track baseline freshness");
        }

        // Resolve covariates from use case class
        String footprint = null;
        CovariateProfile covariateProfile = null;

        if (useCaseClass != null && useCaseClass != Void.class) {
            UseCaseCovariateExtractor extractor = new UseCaseCovariateExtractor();
            CovariateDeclaration declaration = extractor.extractDeclaration(useCaseClass);

            if (!declaration.isEmpty()) {
                Long startTimeMs = store.get("startTimeMs", Long.class);
                Instant startTime = startTimeMs != null
                        ? Instant.ofEpochMilli(startTimeMs)
                        : Instant.now().minusSeconds(60);
                Instant endTime = aggregator.getEndTime();

                // Get use case instance for @CovariateSource resolution
                Object useCaseInstance = null;
                Optional<UseCaseProvider> providerOpt = findUseCaseProvider(context);
                if (providerOpt.isPresent()) {
                    useCaseInstance = providerOpt.get().getCurrentInstance(useCaseClass);
                }

                DefaultCovariateResolutionContext resolutionContext =
                        DefaultCovariateResolutionContext.builder()
                                .experimentTiming(startTime, endTime)
                                .useCaseInstance(useCaseInstance)
                                .build();

                CovariateProfileResolver resolver = new CovariateProfileResolver();
                covariateProfile = resolver.resolve(declaration, resolutionContext);

                FootprintComputer footprintComputer = new FootprintComputer();
                footprint = footprintComputer.computeFootprint(useCaseId, declaration);

                context.publishReportEntry("punit.covariates.count",
                        String.valueOf(covariateProfile.size()));
            }
        }

        UseCaseContext useCaseContext = DefaultUseCaseContext.builder().build();

        EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = generator.generate(
                aggregator,
                context.getTestClass().orElse(null),
                context.getTestMethod().orElse(null),
                useCaseContext,
                expiresInDays,
                footprint,
                covariateProfile
        );

        // Write spec to file using measure-specific output format
        try {
            Path outputPath = resolveOutputPath(useCaseId, footprint, covariateProfile);
            MeasureOutputWriter writer = new MeasureOutputWriter();
            writer.write(baseline, outputPath);

            context.publishReportEntry("punit.spec.outputPath", outputPath.toString());
        } catch (IOException e) {
            context.publishReportEntry("punit.spec.error", e.getMessage());
        }

        // Publish final report
        publishFinalReport(context, aggregator);
    }

    private Path resolveOutputPath(String useCaseId, String footprint, CovariateProfile covariateProfile)
            throws IOException {

        String filename;
        if (footprint != null && !footprint.isEmpty()) {
            BaselineFileNamer namer = new BaselineFileNamer();
            CovariateProfile profile = covariateProfile != null ? covariateProfile : CovariateProfile.empty();
            filename = namer.generateFilename(useCaseId, footprint, profile);
        } else {
            filename = useCaseId.replace('.', '-') + ".yaml";
        }

        String outputDirOverride = System.getProperty("punit.specs.outputDir");
        Path baseDir;
        if (outputDirOverride != null && !outputDirOverride.isEmpty()) {
            baseDir = Paths.get(outputDirOverride);
        } else {
            baseDir = Paths.get(DEFAULT_SPECS_DIR);
        }

        Files.createDirectories(baseDir);
        return baseDir.resolve(filename);
    }

    private void publishFinalReport(ExtensionContext context, ExperimentResultAggregator aggregator) {
        context.publishReportEntry("punit.experiment.complete", "true");
        context.publishReportEntry("punit.useCaseId", aggregator.getUseCaseId());
        context.publishReportEntry("punit.samplesExecuted",
                String.valueOf(aggregator.getSamplesExecuted()));
        context.publishReportEntry("punit.successRate",
                String.format("%.4f", aggregator.getObservedSuccessRate()));
        context.publishReportEntry("punit.standardError",
                String.format("%.4f", aggregator.getStandardError()));
        context.publishReportEntry("punit.terminationReason",
                aggregator.getTerminationReason());
        context.publishReportEntry("punit.elapsedMs",
                String.valueOf(aggregator.getElapsedMs()));
        context.publishReportEntry("punit.totalTokens",
                String.valueOf(aggregator.getTotalTokens()));
    }

    private Optional<UseCaseProvider> findUseCaseProvider(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        for (java.lang.reflect.Field field : testInstance.getClass().getDeclaredFields()) {
            if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    return Optional.of((UseCaseProvider) field.get(testInstance));
                } catch (IllegalAccessException e) {
                    // Continue searching
                }
            }
        }
        return Optional.empty();
    }
}
