package org.javai.punit.experiment.optimize;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorAnnotations;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.OptimizeExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.experiment.model.FactorSuit;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * Strategy for handling @OptimizeExperiment.
 *
 * <p>OPTIMIZE mode iteratively refines a single control factor to find its
 * optimal value. It runs multiple samples per iteration, scores each iteration,
 * and mutates the control factor until termination conditions are met.
 *
 * <p>The optimization loop:
 * <ol>
 *   <li>Execute use case N times per iteration (like MEASURE)</li>
 *   <li>Aggregate outcomes into statistics</li>
 *   <li>Score the aggregate</li>
 *   <li>Record in history</li>
 *   <li>Check termination conditions</li>
 *   <li>Mutate the control factor for next iteration</li>
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

        // Validate mutual exclusivity of initial value options
        if (!annotation.initialControlFactorValue().isEmpty() &&
                !annotation.initialControlFactorSource().isEmpty()) {
            throw new ExtensionConfigurationException(
                    "Cannot specify both initialControlFactorValue and initialControlFactorSource. " +
                            "Use one or the other.");
        }

        return new OptimizeConfig(
                useCaseClass,
                useCaseId,
                annotation.controlFactor(),
                annotation.initialControlFactorValue(),
                annotation.initialControlFactorSource(),
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
        Method testMethod = context.getRequiredTestMethod();

        // Resolve the initial control factor value using priority order:
        // 1. initialControlFactorValue (inline)
        // 2. initialControlFactorSource (method reference)
        // 3. @FactorGetter on use case (fallback)
        Object initialControlFactorValue = resolveInitialControlFactorValue(
                context, optimizeConfig);

        // Determine control factor type
        String controlFactorType = initialControlFactorValue != null
                ? initialControlFactorValue.getClass().getSimpleName()
                : "Object";

        // Check for @InputSource annotation
        InputSource inputSource = testMethod.getAnnotation(InputSource.class);
        List<Object> inputs = null;
        Class<?> inputType = null;
        int effectiveSamplesPerIteration = optimizeConfig.samplesPerIteration();

        if (inputSource != null) {
            // Validate mutual exclusivity: @InputSource and explicit samplesPerIteration cannot both be specified
            // The default value is 20 - if the user changes it to anything else, they're explicitly setting it
            if (optimizeConfig.samplesPerIteration() != OptimizeExperiment.DEFAULT_SAMPLES_PER_ITERATION) {
                throw new ExtensionConfigurationException(
                        "@InputSource and samplesPerIteration are mutually exclusive. " +
                        "When using @InputSource, each iteration tests all inputs exactly once " +
                        "(samplesPerIteration is implicitly equal to the number of inputs). " +
                        "Remove the samplesPerIteration attribute when using @InputSource.");
            }

            inputType = findInputParameterType(testMethod, optimizeConfig.controlFactor());
            InputSourceResolver resolver = new InputSourceResolver();
            inputs = resolver.resolve(inputSource, context.getRequiredTestClass(), inputType);

            if (inputs.isEmpty()) {
                throw new ExtensionConfigurationException(
                        "@InputSource resolved to empty list");
            }

            // With @InputSource, samples per iteration = number of inputs
            effectiveSamplesPerIteration = inputs.size();

            store.put("inputs", inputs);
            store.put("inputType", inputType);
        }

        // Instantiate scorer and mutator
        Scorer<OptimizationIterationAggregate> scorer = instantiateScorer(optimizeConfig.scorerClass());
        FactorMutator<?> mutator = instantiateMutator(optimizeConfig.mutatorClass());

        // Build termination policy
        OptimizeTerminationPolicy terminationPolicy = buildTerminationPolicy(optimizeConfig);

        // Create optimization state
        OptimizeState state = new OptimizeState(
                optimizeConfig.useCaseId(),
                optimizeConfig.experimentId(),
                optimizeConfig.controlFactor(),
                controlFactorType,
                effectiveSamplesPerIteration,
                optimizeConfig.maxIterations(),
                optimizeConfig.objective(),
                scorer,
                mutator,
                terminationPolicy,
                FactorSuit.empty(), // Fixed factors not implemented yet
                initialControlFactorValue
        );

        store.put("mode", ExperimentMode.OPTIMIZE);
        store.put("optimizeState", state);
        store.put("terminated", new AtomicBoolean(false));

        // Create a lazy Spliterator that generates invocation contexts
        Spliterator<TestTemplateInvocationContext> spliterator;
        if (inputs != null) {
            spliterator = new OptimizeWithInputsSpliterator(state, inputs, inputType);
        } else {
            spliterator = new OptimizeSpliterator(state);
        }

        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Finds the input parameter type from method parameters.
     */
    private Class<?> findInputParameterType(Method method, String controlFactorName) {
        for (Parameter param : method.getParameters()) {
            Class<?> type = param.getType();
            if (type == OutcomeCaptor.class) {
                continue;
            }
            // Skip @ControlFactor parameter
            if (param.isAnnotationPresent(org.javai.punit.api.ControlFactor.class)) {
                continue;
            }
            // Skip @Factor parameter
            if (param.isAnnotationPresent(Factor.class)) {
                continue;
            }
            // Skip use case types
            if (type.getPackageName().contains("usecase") ||
                type.getSimpleName().endsWith("UseCase")) {
                continue;
            }
            return type;
        }
        throw new ExtensionConfigurationException(
                "@InputSource requires a method parameter to inject the input value. " +
                "The parameter must not be OutcomeCaptor, UseCase, or @ControlFactor-annotated.");
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
        OutcomeCaptor captor = invocationStore.get("captor", OutcomeCaptor.class);
        Integer iterationNumber = invocationStore.get("iterationNumber", Integer.class);
        Integer sampleInIteration = invocationStore.get("sampleInIteration", Integer.class);

        // Execute the test method
        try {
            invocation.proceed();

            // Record the outcome - use contract outcome directly
            if (captor != null && captor.hasContractOutcome()) {
                state.recordOutcome(captor.getContractOutcome());
            }
        } catch (Throwable e) {
            // Record as failed outcome
            state.recordOutcome(createInvocationFailedOutcome(e));
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

    // === Initial Value Resolution ===

    /**
     * Resolves the initial control factor value using priority order:
     * <ol>
     *   <li>initialControlFactorValue - inline value from annotation</li>
     *   <li>initialControlFactorSource - method reference on experiment class</li>
     *   <li>@FactorGetter on use case - fallback</li>
     * </ol>
     */
    private Object resolveInitialControlFactorValue(ExtensionContext context, OptimizeConfig config) {
        // 1. Try inline value
        if (config.hasInitialValue()) {
            return config.initialControlFactorValue();
        }

        // 2. Try method source
        if (config.hasInitialValueSource()) {
            return resolveFromMethodSource(context, config.initialControlFactorSource());
        }

        // 3. Fall back to @FactorGetter on use case
        Object useCaseInstance = getUseCaseInstance(context, config.useCaseClass());
        return getControlFactorFromUseCase(useCaseInstance, config.useCaseClass(), config.controlFactor());
    }

    /**
     * Resolves initial value from a static method on the experiment class.
     */
    private Object resolveFromMethodSource(ExtensionContext context, String methodName) {
        Class<?> testClass = context.getRequiredTestClass();

        try {
            Method method = testClass.getDeclaredMethod(methodName);
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                throw new ExtensionConfigurationException(
                        "initialControlFactorSource method must be static: " + methodName);
            }
            method.setAccessible(true);
            return method.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new ExtensionConfigurationException(
                    "initialControlFactorSource method not found: " + methodName +
                            " on class " + testClass.getName(), e);
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Failed to invoke initialControlFactorSource method: " + methodName, e);
        }
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

    private Object getControlFactorFromUseCase(Object useCaseInstance, Class<?> useCaseClass, String controlFactor) {
        // Find method annotated with @FactorGetter matching the control factor
        Optional<Method> getterOpt = FactorAnnotations.findFactorGetter(useCaseClass, controlFactor);

        if (getterOpt.isEmpty()) {
            throw new ExtensionConfigurationException(
                    "No @FactorGetter for \"" + controlFactor + "\" found on use case class: " +
                            useCaseClass.getName() + ". Either add a @FactorGetter method, or specify " +
                            "initialControlFactorValue or initialControlFactorSource in @OptimizeExperiment.");
        }

        Method method = getterOpt.get();
        if (method.getParameterCount() != 0) {
            throw new ExtensionConfigurationException(
                    "@FactorGetter method must have no parameters: " + method.getName());
        }

        try {
            return method.invoke(useCaseInstance);
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Failed to get initial control factor value from @FactorGetter method: " +
                            method.getName(), e);
        }
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
        // Use getTestInstance() instead of getRequiredTestInstance() because
        // during provideInvocationContexts(), the test instance may not exist yet
        Optional<Object> testInstanceOpt = context.getTestInstance();
        if (testInstanceOpt.isEmpty()) {
            return Optional.empty();
        }

        Object testInstance = testInstanceOpt.get();
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

    /**
     * Creates a contract UseCaseOutcome for invocation failures.
     *
     * <p>The outcome has a null result and a single failing postcondition
     * indicating the invocation failed with the given exception.
     */
    private UseCaseOutcome<Void> createInvocationFailedOutcome(Throwable e) {
        String errorMessage = "Invocation failed: " + e.getClass().getSimpleName() +
                (e.getMessage() != null ? " - " + e.getMessage() : "");

        return new UseCaseOutcome<>(
                null,
                Duration.ZERO,
                Instant.now(),
                Map.of("error", errorMessage, "exceptionType", e.getClass().getName()),
                new FailedInvocationEvaluator(errorMessage),
                null,
                null,
                null
        );
    }

    /**
     * PostconditionEvaluator for invocation failures.
     */
    private static final class FailedInvocationEvaluator implements org.javai.punit.contract.PostconditionEvaluator<Void> {
        private final String errorMessage;

        FailedInvocationEvaluator(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public List<PostconditionResult> evaluate(Void result) {
            return List.of(PostconditionResult.failed("Invocation succeeded", errorMessage));
        }

        @Override
        public int postconditionCount() {
            return 1;
        }
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
                    state.currentControlFactorValue(),
                    state.controlFactorName(),
                    new OutcomeCaptor()
            );

            action.accept(context);
            return true;
        }
    }

    /**
     * Spliterator that generates OptimizeWithInputsInvocationContext instances.
     *
     * <p>This spliterator iterates through all inputs for each iteration.
     * Each iteration tests every input exactly once, then moves to the next iteration.
     */
    private static class OptimizeWithInputsSpliterator
            extends Spliterators.AbstractSpliterator<TestTemplateInvocationContext> {

        private final OptimizeState state;
        private final List<Object> inputs;
        private final Class<?> inputType;

        OptimizeWithInputsSpliterator(
                OptimizeState state,
                List<Object> inputs,
                Class<?> inputType) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
            this.state = state;
            this.inputs = inputs;
            this.inputType = inputType;
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

            // Advance to next sample (1-indexed)
            int sample = state.nextSample();

            // Input index is sample - 1 (0-indexed)
            int inputIndex = sample - 1;
            Object inputValue = inputs.get(inputIndex);

            // Create invocation context with input
            OptimizeWithInputsInvocationContext context = new OptimizeWithInputsInvocationContext(
                    state.currentIteration(),
                    sample,
                    state.samplesPerIteration(),
                    state.maxIterations(),
                    state.useCaseId(),
                    state.currentControlFactorValue(),
                    state.controlFactorName(),
                    new OutcomeCaptor(),
                    inputValue,
                    inputType,
                    inputIndex,
                    inputs.size()
            );

            action.accept(context);
            return true;
        }
    }
}
