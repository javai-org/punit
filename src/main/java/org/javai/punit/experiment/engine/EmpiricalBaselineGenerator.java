package org.javai.punit.experiment.engine;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import org.javai.punit.api.UseCaseContext;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.EmpiricalBaseline.CostSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.ExecutionSummary;
import org.javai.punit.experiment.model.EmpiricalBaseline.StatisticsSummary;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.ExpirationPolicy;

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
        return generate(aggregator, experimentClass, experimentMethod, context, 0);
    }

    /**
     * Generates an empirical baseline from an experiment's aggregated results.
     *
     * @param aggregator the experiment result aggregator
     * @param experimentClass the experiment class (may be null)
     * @param experimentMethod the experiment method (may be null)
     * @param context the use case context
     * @param expiresInDays the validity period in days (0 = no expiration)
     * @return the generated baseline
     */
    public EmpiricalBaseline generate(
            ExperimentResultAggregator aggregator,
            Class<?> experimentClass,
            Method experimentMethod,
            UseCaseContext context,
            int expiresInDays) {
        return generate(aggregator, experimentClass, experimentMethod, context, expiresInDays, null, null);
    }

    /**
     * Generates an empirical baseline from an experiment's aggregated results with covariate support.
     *
     * @param aggregator the experiment result aggregator
     * @param experimentClass the experiment class (may be null)
     * @param experimentMethod the experiment method (may be null)
     * @param context the use case context
     * @param expiresInDays the validity period in days (0 = no expiration)
     * @param footprint the invocation footprint hash (may be null)
     * @param covariateProfile the resolved covariate profile (may be null)
     * @return the generated baseline
     */
    public EmpiricalBaseline generate(
            ExperimentResultAggregator aggregator,
            Class<?> experimentClass,
            Method experimentMethod,
            UseCaseContext context,
            int expiresInDays,
            String footprint,
            CovariateProfile covariateProfile) {
        
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
        
        // Include postcondition pass rates if available
        Map<String, Double> criteriaPassRates = aggregator.hasPostconditionStats()
            ? aggregator.getPostconditionPassRates()
            : Map.of();
        
        StatisticsSummary statistics = new StatisticsSummary(
            aggregator.getObservedSuccessRate(),
            aggregator.getStandardError(),
            ci[0],
            ci[1],
            aggregator.getSuccesses(),
            aggregator.getFailures(),
            failureDistribution,
            criteriaPassRates
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

        // Add expiration policy if specified
        if (expiresInDays > 0) {
            Instant endTime = aggregator.getEndTime();
            builder.expirationPolicy(ExpirationPolicy.of(expiresInDays, endTime));
        }
        
        // Add covariate information if present
        if (footprint != null && !footprint.isEmpty()) {
            builder.footprint(footprint);
        }
        if (covariateProfile != null && !covariateProfile.isEmpty()) {
            builder.covariateProfile(covariateProfile);
        }
        
        return builder.build();
    }
}

