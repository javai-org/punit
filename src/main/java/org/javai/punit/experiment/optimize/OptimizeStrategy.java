package org.javai.punit.experiment.optimize;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.OptimizeExperiment;
import org.javai.punit.api.ResultCaptor;
import org.javai.punit.api.TreatmentValueSource;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.model.FactorSuit;
import org.javai.punit.model.UseCaseOutcome;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy for handling @OptimizeExperiment.
 *
 * <p>OPTIMIZE mode iteratively refines a single treatment factor to find its
 * optimal value. It runs multiple samples per iteration, scores each iteration,
 * and mutates the treatment factor until termination conditions are met.
 *
 * <p>The optimization loop:
 * <ol>
 *   <li>Execute use case N times per iteration (like MEASURE)</li>
 *   <li>Aggregate outcomes into statistics</li>
 *   <li>Score the aggregate</li>
 *   <li>Record in history</li>
 *   <li>Check termination conditions</li>
 *   <li>Mutate the treatment factor for next iteration</li>
 *   <li>Repeat until terminated</li>
 * </ol>
 */
public class OptimizeStrategy implements ExperimentModeStrategy {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("org.javai.punit.experiment");

    @Override
    public boolean supports(Method testMethod) {
        return testMethod.isAnnotationPresent(OptimizeExperiment.class);
    }

    @Override
    public ExperimentConfig parseConfig(Method testMethod) {
        OptimizeExperiment annotation = testMethod.getAnnotation(OptimizeExperiment.class);
        if (annotation == null) {
            throw new ExtensionConfigurationException(
                    "Method must be annotated with @OptimizeExperiment");
        }

        Class<?> useCaseClass = annotation.useCase();
        String useCaseId = UseCaseProvider.resolveId(useCaseClass);

        return new OptimizeConfig(
                useCaseClass,
                useCaseId,
                annotation.treatmentFactor(),
                annotation.scorer(),
                annotation.mutator(),
                annotation.objective(),
                annotation.samplesPerIteration(),
                annotation.maxIterations(),
                annotation.noImprovementWindow(),
                annotation.timeBudgetMs(),
                annotation.tokenBudget(),
                annotation.experimentId()
        );
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideInvocationContexts(
            ExperimentConfig config,
            ExtensionContext context,
            ExtensionContext.Store store) {

        OptimizeConfig optimizeConfig = (OptimizeConfig) config;

        // Get the use case instance to retrieve the initial treatment value
        Object useCaseInstance = getUseCaseInstance(context, optimizeConfig.useCaseClass());
        Object initialTreatmentValue = getInitialTreatmentValue(
                useCaseInstance,
                optimizeConfig.useCaseClass(),
                optimizeConfig.treatmentFactor()
        );

        // Determine treatment factor type
        String treatmentFactorType = initialTreatmentValue != null
                ? initialTreatmentValue.getClass().getSimpleName()
                : "Object";

        // Instantiate scorer and mutator
        Scorer<OptimizationIterationAggregate> scorer = instantiateScorer(optimizeConfig.scorerClass());
        FactorMutator<?> mutator = instantiateMutator(optimizeConfig.mutatorClass());

        // Build termination policy
        OptimizeTerminationPolicy terminationPolicy = buildTerminationPolicy(optimizeConfig);

        // Create optimization state
        OptimizeState state = new OptimizeState(
                optimizeConfig.useCaseId(),
                optimizeConfig.experimentId(),
                optimizeConfig.treatmentFactor(),
                treatmentFactorType,
                optimizeConfig.samplesPerIteration(),
                optimizeConfig.maxIterations(),
                optimizeConfig.objective(),
                scorer,
                mutator,
                terminationPolicy,
                FactorSuit.empty(), // Fixed factors not implemented yet
                initialTreatmentValue
        );

        store.put("mode", ExperimentMode.OPTIMIZE);
        store.put("optimizeState", state);
        store.put("terminated", new AtomicBoolean(false));

        // Create a lazy Spliterator that generates invocation contexts
        Spliterator<TestTemplateInvocationContext> spliterator = new OptimizeSpliterator(state);

        return StreamSupport.stream(spliterator, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void intercept(
            InvocationInterceptor.Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {

        OptimizeState state = store.get("optimizeState", OptimizeState.class);
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);

        // Get invocation-specific data from the invocation context store
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        ResultCaptor captor = invocationStore.get("captor", ResultCaptor.class);
        Integer iterationNumber = invocationStore.get("iterationNumber", Integer.class);
        Integer sampleInIteration = invocationStore.get("sampleInIteration", Integer.class);

        // Execute the test method
        try {
            invocation.proceed();

            // Record the outcome
            if (captor != null && captor.hasResult()) {
                Object result = captor.getResult();
                if (result instanceof UseCaseOutcome outcome) {
                    state.recordOutcome(outcome);
                }
            }
        } catch (Throwable e) {
            // Record as failed outcome
            state.recordOutcome(UseCaseOutcome.invocationFailed(e));
            // Don't rethrow - allow experiment to continue
        }

        // Report progress
        reportProgress(extensionContext, state, iterationNumber, sampleInIteration);

        // Check if this is the last sample of the iteration
        if (state.isLastSampleOfIteration()) {
            boolean shouldContinue = state.completeIteration();
            if (!shouldContinue) {
                terminated.set(true);
                // Generate output
                generateOutput(extensionContext, store, state);
            }
        }
    }

    @Override
    public int computeTotalSamples(ExperimentConfig config, Method testMethod) {
        OptimizeConfig optimizeConfig = (OptimizeConfig) config;
        return optimizeConfig.maxTotalSamples();
    }

    // === Helper Methods ===

    private Object getUseCaseInstance(ExtensionContext context, Class<?> useCaseClass) {
        if (useCaseClass == Void.class) {
            throw new ExtensionConfigurationException(
                    "@OptimizeExperiment requires useCase to be specified");
        }

        // Try to get from UseCaseProvider
        Optional<UseCaseProvider> providerOpt = findUseCaseProvider(context);
        if (providerOpt.isPresent()) {
            UseCaseProvider provider = providerOpt.get();
            if (provider.isRegistered(useCaseClass)) {
                return provider.getInstance(useCaseClass);
            }
        }

        // Try to instantiate directly
        try {
            return useCaseClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Cannot instantiate use case class: " + useCaseClass.getName() +
                            ". Either register it with UseCaseProvider or provide a no-arg constructor.", e);
        }
    }

    private Object getInitialTreatmentValue(Object useCaseInstance, Class<?> useCaseClass, String treatmentFactor) {
        // Find method annotated with @TreatmentValueSource matching the treatment factor
        for (Method method : useCaseClass.getMethods()) {
            TreatmentValueSource annotation = method.getAnnotation(TreatmentValueSource.class);
            if (annotation != null && annotation.value().equals(treatmentFactor)) {
                if (method.getParameterCount() != 0) {
                    throw new ExtensionConfigurationException(
                            "@TreatmentValueSource method must have no parameters: " + method.getName());
                }
                try {
                    return method.invoke(useCaseInstance);
                } catch (Exception e) {
                    throw new ExtensionConfigurationException(
                            "Failed to get initial treatment value from @TreatmentValueSource method: " +
                                    method.getName(), e);
                }
            }
        }

        throw new ExtensionConfigurationException(
                "No @TreatmentValueSource(\"" + treatmentFactor + "\") method found on use case class: " +
                        useCaseClass.getName() + ". Add a method like: " +
                        "@TreatmentValueSource(\"" + treatmentFactor + "\") public T get" +
                        capitalize(treatmentFactor) + "() { ... }");
    }

    @SuppressWarnings("unchecked")
    private Scorer<OptimizationIterationAggregate> instantiateScorer(
            Class<? extends Scorer<OptimizationIterationAggregate>> scorerClass) {
        try {
            return scorerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Cannot instantiate scorer: " + scorerClass.getName() +
                            ". Ensure it has a public no-arg constructor.", e);
        }
    }

    private FactorMutator<?> instantiateMutator(Class<? extends FactorMutator<?>> mutatorClass) {
        try {
            return mutatorClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Cannot instantiate mutator: " + mutatorClass.getName() +
                            ". Ensure it has a public no-arg constructor.", e);
        }
    }

    private OptimizeTerminationPolicy buildTerminationPolicy(OptimizeConfig config) {
        java.util.List<OptimizeTerminationPolicy> policies = new java.util.ArrayList<>();

        policies.add(new OptimizationMaxIterationsPolicy(config.maxIterations()));

        if (config.noImprovementWindow() > 0) {
            policies.add(new OptimizationNoImprovementPolicy(config.noImprovementWindow()));
        }

        if (config.timeBudgetMs() > 0) {
            policies.add(new OptimizeTimeBudgetPolicy(
                    java.time.Duration.ofMillis(config.timeBudgetMs())));
        }

        return new OptimizeCompositeTerminationPolicy(policies);
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

    private void reportProgress(ExtensionContext context, OptimizeState state,
                                Integer iteration, Integer sample) {
        context.publishReportEntry("punit.mode", "OPTIMIZE");
        context.publishReportEntry("punit.iteration", String.valueOf(iteration + 1));
        context.publishReportEntry("punit.sample",
                sample + "/" + state.samplesPerIteration());

        OptimizeHistory partial = state.buildPartialHistory();
        partial.bestScore().ifPresent(bestScore ->
                context.publishReportEntry("punit.bestScore", String.format("%.4f", bestScore)));
    }

    private void generateOutput(ExtensionContext context, ExtensionContext.Store store, OptimizeState state) {
        OptimizeHistory history = state.buildHistory();
        store.put("optimizationHistory", history);

        // Generate spec file
        OptimizeSpecGenerator generator = new OptimizeSpecGenerator();
        generator.generateSpec(context, history);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Spliterator that lazily generates OptimizeInvocationContext instances.
     *
     * <p>This spliterator tracks the current iteration and sample numbers,
     * generating contexts for each sample. After the last sample of an iteration
     * is consumed and processed by intercept(), the iteration transition
     * (aggregation, scoring, mutation) happens in intercept(). This spliterator
     * then checks the termination flag before generating contexts for the next iteration.
     */
    private static class OptimizeSpliterator
            extends Spliterators.AbstractSpliterator<TestTemplateInvocationContext> {

        private final OptimizeState state;

        OptimizeSpliterator(OptimizeState state) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
            this.state = state;
        }

        @Override
        public boolean tryAdvance(Consumer<? super TestTemplateInvocationContext> action) {
            // Check if terminated
            if (state.isTerminated()) {
                return false;
            }

            // Check if we've exceeded max iterations
            if (state.currentIteration() >= state.maxIterations()) {
                return false;
            }

            // Advance to next sample
            int sample = state.nextSample();

            // Create invocation context
            OptimizeInvocationContext context = new OptimizeInvocationContext(
                    state.currentIteration(),
                    sample,
                    state.samplesPerIteration(),
                    state.maxIterations(),
                    state.useCaseId(),
                    state.currentTreatmentValue(),
                    state.treatmentFactorName(),
                    new ResultCaptor()
            );

            action.accept(context);
            return true;
        }
    }
}
