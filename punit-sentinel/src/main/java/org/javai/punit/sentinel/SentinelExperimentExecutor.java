package org.javai.punit.sentinel;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.experiment.engine.EmpiricalBaselineGenerator;
import org.javai.punit.experiment.engine.ExperimentResultAggregator;
import org.javai.punit.experiment.measure.MeasureOutputWriter;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.usecase.UseCaseFactory;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.ProbabilisticTestVerdictBuilder;

/**
 * Executes a single {@code @MeasureExperiment} method in the Sentinel runtime,
 * producing a {@link ProbabilisticTestVerdict} and writing baseline spec files.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Budget monitor creation from annotation values</li>
 *   <li>Delegation to {@link SentinelSampleExecutor} for the experiment sample loop</li>
 *   <li>Outcome aggregation and empirical baseline generation</li>
 *   <li>Spec file writing (functional and latency) to the resolved output directory</li>
 *   <li>Experiment report entry construction</li>
 * </ul>
 *
 * <p>Package-private: constructed and used exclusively by {@link SentinelRunner}.
 */
class SentinelExperimentExecutor {

    private static final Logger logger = LogManager.getLogger(SentinelExperimentExecutor.class);

    private final SentinelSampleExecutor sampleExecutor;
    private final SentinelClassIntrospector introspector;
    private final EnvironmentMetadata environmentMetadata;
    private SentinelProgressListener progressListener;

    SentinelExperimentExecutor(
            SentinelSampleExecutor sampleExecutor,
            SentinelClassIntrospector introspector,
            EnvironmentMetadata environmentMetadata) {
        this.sampleExecutor = sampleExecutor;
        this.introspector = introspector;
        this.environmentMetadata = environmentMetadata;
    }

    void setProgressListener(SentinelProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Executes a single measure experiment method, writes baseline specs,
     * and returns the verdict.
     *
     * @param method the experiment method
     * @param annotation the method's {@code @MeasureExperiment} annotation
     * @param instance the sentinel class instance
     * @param factory the use case factory
     * @param sentinelClass the sentinel class (for naming, input resolution, and baseline generation)
     * @return the probabilistic test verdict
     */
    ProbabilisticTestVerdict execute(
            Method method,
            MeasureExperiment annotation,
            Object instance,
            UseCaseFactory factory,
            Class<?> sentinelClass) {

        String experimentName = sentinelClass.getSimpleName() + "." + method.getName();
        Class<?> useCaseClass = annotation.useCase();
        String useCaseId = UseCaseFactory.resolveId(useCaseClass);
        UseCaseAttributes useCaseAttributes = UseCaseFactory.resolveAttributes(useCaseClass);
        logger.info("Executing experiment: {} (useCase: {})", experimentName, useCaseId);

        List<Object> inputs = introspector.resolveInputs(method, sentinelClass);
        int samples = annotation.samples();
        CostBudgetMonitor budgetMonitor = createBudgetMonitor(annotation);

        if (progressListener != null) {
            progressListener.onMethodStart(experimentName, samples);
        }

        List<OutcomeCaptor> capturedOutcomes = new ArrayList<>();
        sampleExecutor.executeExperimentLoop(
                method, instance, factory, useCaseClass,
                inputs, useCaseAttributes.warmup(), samples, capturedOutcomes, budgetMonitor, progressListener);

        ExperimentResultAggregator aggregator = aggregate(capturedOutcomes, useCaseId);

        boolean specWritten = writeSpec(
                aggregator, useCaseId, sentinelClass, method, annotation, useCaseAttributes);

        if (progressListener != null) {
            progressListener.onExperimentComplete(
                    experimentName, aggregator.getSamplesExecuted(),
                    aggregator.getSuccesses());
        }

        double observedRate = aggregator.getSamplesExecuted() > 0
                ? (double) aggregator.getSuccesses() / aggregator.getSamplesExecuted()
                : 0.0;

        return new ProbabilisticTestVerdictBuilder()
                .identity(sentinelClass.getName(), method.getName(), useCaseId)
                .execution(
                        samples,
                        aggregator.getSamplesExecuted(),
                        aggregator.getSuccesses(),
                        aggregator.getFailures(),
                        0.0,
                        observedRate,
                        0)
                .useCaseAttributes(useCaseAttributes)
                .termination(TerminationReason.COMPLETED, null)
                .junitPassed(specWritten)
                .passedStatistically(specWritten)
                .environmentMetadata(environmentMetadata.toMap())
                .build();
    }

    private CostBudgetMonitor createBudgetMonitor(MeasureExperiment annotation) {
        if (annotation.timeBudgetMs() <= 0 && annotation.tokenBudget() <= 0) {
            return null;
        }
        return new CostBudgetMonitor(
                annotation.timeBudgetMs(), annotation.tokenBudget(),
                0, CostBudgetMonitor.TokenMode.NONE,
                BudgetExhaustedBehavior.FAIL);
    }

    private ExperimentResultAggregator aggregate(
            List<OutcomeCaptor> capturedOutcomes, String useCaseId) {

        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(
                useCaseId, capturedOutcomes.size());

        for (OutcomeCaptor captor : capturedOutcomes) {
            if (captor.hasResult()) {
                UseCaseOutcome<?> outcome = captor.getContractOutcome();
                if (outcome.allPostconditionsSatisfied()) {
                    aggregator.recordSuccess(outcome);
                } else {
                    aggregator.recordFailure(outcome, "postcondition_failure");
                }
            } else if (captor.hasException()) {
                aggregator.recordException(captor.getException());
            }
        }
        aggregator.setCompleted();
        return aggregator;
    }

    private boolean writeSpec(
            ExperimentResultAggregator aggregator,
            String useCaseId,
            Class<?> sentinelClass,
            Method method,
            MeasureExperiment annotation,
            UseCaseAttributes useCaseAttributes) {

        EmpiricalBaselineGenerator baselineGenerator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = baselineGenerator.generate(
                aggregator, sentinelClass, method, null, annotation.expiresInDays(),
                null, null, useCaseAttributes);

        try {
            Path specDir = resolveSpecOutputDir();
            Files.createDirectories(specDir);
            MeasureOutputWriter writer = new MeasureOutputWriter();

            Path functionalPath = specDir.resolve(useCaseId.replace('.', '-') + ".yaml");
            writer.writeFunctional(baseline, functionalPath);
            logger.info("Functional spec written to {}", functionalPath);

            if (baseline.hasLatencyDistribution()) {
                Path latencyPath = specDir.resolve(useCaseId.replace('.', '-') + ".latency.yaml");
                writer.writeLatency(baseline, latencyPath);
                logger.info("Latency spec written to {}", latencyPath);
            }

            return true;
        } catch (IOException e) {
            logger.error("Failed to write spec for {}: {}", useCaseId, e.getMessage(), e);
            return false;
        }
    }

    Path resolveSpecOutputDir() {
        String dir = System.getProperty("punit.spec.dir");
        if (dir != null && !dir.isEmpty()) {
            return Paths.get(dir);
        }
        dir = System.getenv("PUNIT_SPEC_DIR");
        if (dir != null && !dir.isEmpty()) {
            return Paths.get(dir);
        }
        return Paths.get("punit/specs");
    }

}
