package org.javai.punit.experiment.engine;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.EmpiricalBaseline.CostSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.ExecutionSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.StatisticsSummary;

/**
 * Generates empirical baselines from experiment results.
 */
public class EmpiricalBaselineGenerator {
    
    /**
     * Generates an empirical baseline from an experiment's aggregated results.
     *
     * @param aggregator the experiment result aggregator
     * @param experimentClass the experiment class (may be null)
     * @param experimentMethod the experiment method (may be null)
     * @param context the use case context
     * @return the generated baseline
     */
    public EmpiricalBaseline generate(
            ExperimentResultAggregator aggregator,
            Class<?> experimentClass,
            Method experimentMethod,
            UseCaseContext context) {
        
        double[] ci = aggregator.getConfidenceInterval95();
        
        ExecutionSummary execution = new ExecutionSummary(
            aggregator.getTotalSamples(),
            aggregator.getSamplesExecuted(),
            aggregator.getTerminationReason(),
            aggregator.getTerminationDetails()
        );
        
        // EXPLORE mode: omit failure distribution (noisy with small samples, breaks diff alignment)
        // MEASURE mode: include failure distribution (useful for diagnosing failure patterns)
        boolean isExploreMode = aggregator.hasResultProjections();
        Map<String, Integer> failureDistribution = isExploreMode 
            ? Map.of() 
            : aggregator.getFailureDistribution();
        
        StatisticsSummary statistics = new StatisticsSummary(
            aggregator.getObservedSuccessRate(),
            aggregator.getStandardError(),
            ci[0],
            ci[1],
            aggregator.getSuccesses(),
            aggregator.getFailures(),
            failureDistribution
        );
        
        CostSummary cost = new CostSummary(
            aggregator.getElapsedMs(),
            aggregator.getAvgTimePerSampleMs(),
            aggregator.getTotalTokens(),
            aggregator.getAvgTokensPerSample()
        );
        
        EmpiricalBaseline.Builder builder = EmpiricalBaseline.builder()
            .useCaseId(aggregator.getUseCaseId())
            .generatedAt(Instant.now())
            .execution(execution)
            .statistics(statistics)
            .cost(cost);
        
        if (experimentClass != null) {
            builder.experimentClass(experimentClass.getName());
        }
        
        if (experimentMethod != null) {
            builder.experimentMethod(experimentMethod.getName());
        }
        
        // Add result projections (EXPLORE mode only)
        if (aggregator.hasResultProjections()) {
            builder.resultProjections(aggregator.getResultProjections());
        }
        
        return builder.build();
    }
}

