package org.javai.punit.experiment.optimize;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.javai.punit.api.ExperimentMode;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.legacy.OptimizeExperiment;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.engine.ExperimentConfig;
import org.javai.punit.experiment.engine.ExperimentModeStrategy;
import org.javai.punit.experiment.engine.ExperimentWarmupHandler;
import org.javai.punit.experiment.engine.WarmupInvocationContext;
import org.javai.punit.experiment.engine.input.InputParameterDetector;
import org.javai.punit.experiment.engine.input.InputSourceResolver;
import org.javai.punit.experiment.model.FactorSuit;
import org.javai.punit.model.UseCaseAttributes;
import org.javai.punit.usecase.UseCaseFactory;
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

    private final ExperimentWarmupHandler warmupHandler = new ExperimentWarmupHandler();

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
        String useCaseId = UseCaseFactory.resolveId(useCaseClass);
        UseCaseAttributes useCaseAttributes = UseCaseFactory.resolveAttributes(useCaseClass);
        if (annotation.skipWarmup()) {
            useCaseAttributes = new UseCaseAttributes(0, useCaseAttributes.maxConcurrent());
        }

        if (annotation.initialFactor().isEmpty()) {
            throw new ExtensionConfigurationException(
                    "@OptimizeExperiment requires initialFactor to be set to the name of " +
                            "a static no-arg method on the experiment class that returns " +
                            "the starting control factor value.");
        }

        return new OptimizeConfig(
                useCaseClass,
                useCaseId,
                annotation.controlFactor(),
                annotation.initialFactor(),
                annotation.scorer(),
                annotation.mutator(),
                annotation.objective(),
                useCaseAttributes,
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

        // Resolve the initial control factor value from the initialFactor method.
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
            inputType = InputParameterDetector.findInputParameterType(testMethod);
            InputSourceResolver resolver = new InputSourceResolver();
            inputs = resolver.resolve(inputSource, context.getRequiredTestClass(), inputType);

            if (inputs.isEmpty()) {
                throw new ExtensionConfigurationException(
                        "@InputSource resolved to empty list");
            }

            // When samplesPerIteration is at default and @InputSource is present,
            // use inputs.size() for backward compatibility
            if (optimizeConfig.samplesPerIteration() == OptimizeExperiment.DEFAULT_SAMPLES_PER_ITERATION) {
                effectiveSamplesPerIteration = inputs.size();
            }
            // Otherwise, use the explicitly set samplesPerIteration (round-robin handles any mismatch)

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
        store.put("warmupCounter", new java.util.concurrent.atomic.AtomicInteger(0));

        // Create a lazy Spliterator that generates invocation contexts
        Spliterator<TestTemplateInvocationContext> spliterator;
        if (inputs != null) {
            spliterator = new OptimizeWithInputsSpliterator(state, inputs, inputType);
        } else {
            spliterator = new OptimizeSpliterator(state);
        }

        Stream<TestTemplateInvocationContext> sampleStream = StreamSupport.stream(spliterator, false);

        // Prepend warmup contexts if configured
        int warmup = optimizeConfig.warmup();
        if (warmup > 0) {
            final List<Object> warmupInputs = inputs;
            final Class<?> warmupInputType = inputType;
            Stream<TestTemplateInvocationContext> warmupStream = Stream.iterate(1, i -> i + 1)
                    .limit(warmup)
                    .map(i -> warmupInputs != null
                            ? WarmupInvocationContext.forExperimentWithInput(
                                    i, warmup, warmupInputs.get(0), warmupInputType)
                            : WarmupInvocationContext.forExperiment(i, warmup));
            return Stream.concat(warmupStream, sampleStream);
        }
        return sampleStream;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void intercept(
            InvocationInterceptor.Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {

        OptimizeConfig config = (OptimizeConfig) store.get("config", ExperimentConfig.class);
        OptimizeState state = store.get("optimizeState", OptimizeState.class);
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);

        // Warmup gate
        int warmup = config != null ? config.warmup() : 0;
        ExperimentWarmupHandler.WarmupResult warmupResult = warmupHandler.handle(
                invocation, store, warmup, 0, null);
        if (warmupResult.handled()) {
            return;
        }

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

    // === Initial Factor Resolution ===

    /**
     * Resolves the initial control factor value by invoking the static no-arg
     * method named by {@link OptimizeExperiment#initialFactor()} on the
     * experiment class.
     */
    private Object resolveInitialControlFactorValue(ExtensionContext context, OptimizeConfig config) {
        Class<?> testClass = context.getRequiredTestClass();
        String methodName = config.initialFactor();

        try {
            Method method = testClass.getDeclaredMethod(methodName);
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                throw new ExtensionConfigurationException(
                        "initialFactor method must be static: " + methodName);
            }
            method.setAccessible(true);
            return method.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new ExtensionConfigurationException(
                    "initialFactor method not found: " + methodName +
                            " on class " + testClass.getName(), e);
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Failed to invoke initialFactor method: " + methodName, e);
        }
    }

    // === Helper Methods ===

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
     * <p>This spliterator cycles through inputs via round-robin for each iteration.
     * Inputs are selected using modulo indexing: {@code (sample - 1) % inputs.size()}.
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

            // Round-robin cycling through inputs
            int inputIndex = (sample - 1) % inputs.size();
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
