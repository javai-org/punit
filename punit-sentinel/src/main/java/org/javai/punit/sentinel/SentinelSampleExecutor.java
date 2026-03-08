package org.javai.punit.sentinel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.contract.AssertionScope;
import org.javai.punit.controls.budget.CostBudgetMonitor;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.bernoulli.EarlyTerminationEvaluator;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.javai.punit.usecase.UseCaseFactory;

/**
 * Executes the N-sample loop for a single probabilistic test or experiment method.
 *
 * <p>For each sample:
 * <ol>
 *   <li>Begin an {@link AssertionScope} for dimension tracking</li>
 *   <li>Resolve parameters (use case instance, input, outcome captor)</li>
 *   <li>Invoke the method via reflection</li>
 *   <li>Record success/failure and latency in the aggregator</li>
 *   <li>Record dimension results from the assertion scope</li>
 *   <li>Check early termination and budget constraints</li>
 * </ol>
 *
 * <p>Package-private: internal implementation detail of the Sentinel runner.
 */
class SentinelSampleExecutor {

    /**
     * Executes the sample loop for a probabilistic test method.
     *
     * @param method the test method to invoke
     * @param instance the sentinel class instance
     * @param factory the use case factory
     * @param useCaseClass the use case class from the annotation
     * @param inputs the input data (empty if no @InputSource)
     * @param samples the number of samples to execute
     * @param aggregator the result aggregator
     * @param evaluator the early termination evaluator
     * @param budgetMonitor the budget monitor (may be null)
     * @param onException the exception handling policy
     */
    void executeTestLoop(
            Method method,
            Object instance,
            UseCaseFactory factory,
            Class<?> useCaseClass,
            List<Object> inputs,
            int samples,
            SampleResultAggregator aggregator,
            EarlyTerminationEvaluator evaluator,
            CostBudgetMonitor budgetMonitor,
            ExceptionHandling onException) {

        for (int i = 0; i < samples; i++) {
            // Check time budget before sample
            if (budgetMonitor != null) {
                Optional<TerminationReason> budgetReason = budgetMonitor.checkTimeBudget();
                if (budgetReason.isPresent()) {
                    aggregator.setTerminated(budgetReason.get(), "Time budget exhausted");
                    return;
                }
                budgetReason = budgetMonitor.checkTokenBudgetBeforeSample();
                if (budgetReason.isPresent()) {
                    aggregator.setTerminated(budgetReason.get(), "Token budget exhausted");
                    return;
                }
            }

            executeSingleSample(method, instance, factory, useCaseClass, inputs, i,
                    aggregator, onException);

            // Record static token charge
            if (budgetMonitor != null) {
                budgetMonitor.recordStaticTokenCharge();
                Optional<TerminationReason> postBudget = budgetMonitor.checkTokenBudgetAfterSample();
                if (postBudget.isPresent()) {
                    aggregator.setTerminated(postBudget.get(), "Token budget exhausted after sample");
                    return;
                }
            }

            // Check early termination
            Optional<TerminationReason> termination = evaluator.shouldTerminate(
                    aggregator.getSuccesses(), aggregator.getSamplesExecuted());
            if (termination.isPresent()) {
                aggregator.setTerminated(termination.get(), termination.get().getDescription());
                return;
            }
        }
        aggregator.setCompleted();
    }

    /**
     * Executes the sample loop for a measure experiment method.
     *
     * @param method the experiment method to invoke
     * @param instance the sentinel class instance
     * @param factory the use case factory
     * @param useCaseClass the use case class from the annotation
     * @param inputs the input data (empty if no @InputSource)
     * @param samples the number of samples to execute
     * @param capturedOutcomes list to collect outcome captors from each sample
     * @param budgetMonitor the budget monitor (may be null)
     */
    void executeExperimentLoop(
            Method method,
            Object instance,
            UseCaseFactory factory,
            Class<?> useCaseClass,
            List<Object> inputs,
            int samples,
            List<OutcomeCaptor> capturedOutcomes,
            CostBudgetMonitor budgetMonitor) {

        for (int i = 0; i < samples; i++) {
            // Check budget before sample
            if (budgetMonitor != null) {
                Optional<TerminationReason> budgetReason = budgetMonitor.checkTimeBudget();
                if (budgetReason.isPresent()) {
                    return;
                }
            }

            OutcomeCaptor captor = new OutcomeCaptor();
            Object useCaseInstance = resolveUseCaseInstance(factory, useCaseClass);
            Object input = resolveInput(inputs, i);
            Object[] args = buildExperimentArgs(method, useCaseClass, useCaseInstance, input, captor);

            try {
                method.invoke(instance, args);
                capturedOutcomes.add(captor);
            } catch (Exception e) {
                // Record exception in captor for the experiment to handle
                captor.recordException(e.getCause() != null ? e.getCause() : e);
                capturedOutcomes.add(captor);
            }
        }
    }

    private void executeSingleSample(
            Method method,
            Object instance,
            UseCaseFactory factory,
            Class<?> useCaseClass,
            List<Object> inputs,
            int sampleIndex,
            SampleResultAggregator aggregator,
            ExceptionHandling onException) {

        AssertionScope.begin();
        try {
            Object useCaseInstance = resolveUseCaseInstance(factory, useCaseClass);
            Object input = resolveInput(inputs, sampleIndex);
            Object[] args = buildTestArgs(method, useCaseClass, useCaseInstance, input);

            long startNanos = System.nanoTime();
            method.invoke(instance, args);
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            aggregator.recordSuccess(latencyMs);
        } catch (AssertionError e) {
            aggregator.recordFailure(e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AssertionError) {
                aggregator.recordFailure(cause);
            } else {
                handleException(cause, aggregator, onException);
            }
        } catch (Exception e) {
            handleException(e, aggregator, onException);
        } finally {
            recordDimensionResults(aggregator);
            AssertionScope.end();
        }
    }

    private void handleException(
            Throwable cause,
            SampleResultAggregator aggregator,
            ExceptionHandling onException) {
        if (onException == ExceptionHandling.ABORT_TEST) {
            aggregator.recordFailure(cause);
            aggregator.setTerminated(TerminationReason.COMPLETED, "Aborted due to exception: " + cause.getMessage());
        } else {
            // FAIL_SAMPLE — record as failure and continue
            aggregator.recordFailure(cause);
        }
    }

    private void recordDimensionResults(SampleResultAggregator aggregator) {
        AssertionScope scope = AssertionScope.current();
        if (scope == null) {
            return;
        }
        if (scope.isFunctionalAsserted()) {
            aggregator.recordFunctionalResult(scope.isFunctionalPassed());
        }
        if (scope.isLatencyAsserted()) {
            aggregator.recordLatencyResult(scope.isLatencyPassed());
        }
    }

    private Object resolveUseCaseInstance(UseCaseFactory factory, Class<?> useCaseClass) {
        if (useCaseClass == null || useCaseClass == Void.class) {
            return null;
        }
        return factory.getInstance(useCaseClass);
    }

    private Object resolveInput(List<Object> inputs, int sampleIndex) {
        if (inputs.isEmpty()) {
            return null;
        }
        return inputs.get(sampleIndex % inputs.size());
    }

    /**
     * Builds the argument array for a test method invocation.
     *
     * <p>Parameters are matched by type: the use case instance is injected into
     * the parameter matching the use case class; the input value is injected
     * into the remaining parameter.
     */
    private Object[] buildTestArgs(Method method, Class<?> useCaseClass,
                                   Object useCaseInstance, Object input) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == useCaseClass) {
                args[i] = useCaseInstance;
            } else {
                args[i] = input;
            }
        }
        return args;
    }

    /**
     * Builds the argument array for an experiment method invocation.
     *
     * <p>Parameters are matched by type: use case, input, and {@link OutcomeCaptor}.
     */
    private Object[] buildExperimentArgs(Method method, Class<?> useCaseClass,
                                         Object useCaseInstance, Object input,
                                         OutcomeCaptor captor) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == useCaseClass) {
                args[i] = useCaseInstance;
            } else if (OutcomeCaptor.class.isAssignableFrom(paramTypes[i])) {
                args[i] = captor;
            } else {
                args[i] = input;
            }
        }
        return args;
    }
}
