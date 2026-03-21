package org.javai.punit.ptest.concurrent;

import java.lang.reflect.Method;
import java.util.List;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.TokenChargeRecorder;
import org.javai.punit.contract.AssertionScope;
import org.javai.punit.controls.budget.DefaultTokenChargeRecorder;


/**
 * SampleInvoker implementation that invokes the test method reflectively.
 *
 * <p>Wraps the existing SampleExecutor logic: assertion scope management,
 * latency measurement, exception handling, and dimension result capture.
 *
 * <p>Each worker thread gets its own invocation via this class. The
 * {@link AssertionScope} is thread-local, so each worker's assertion
 * scope is isolated.
 */
public class ReflectiveSampleInvoker implements SampleInvoker {

    private final Object testInstance;
    private final Method testMethod;
    private final ExceptionHandling exceptionPolicy;
    private final List<Object> inputs;
    private final Class<?> inputType;
    private final boolean hasTokenRecorderParam;
    private final long tokenBudget;

    /**
     * @param testInstance        the test class instance
     * @param testMethod          the test method to invoke
     * @param exceptionPolicy     how to handle non-assertion exceptions
     * @param inputs              resolved inputs for @InputSource (null if no @InputSource)
     * @param inputType           the input parameter type (null if no @InputSource)
     * @param hasTokenRecorderParam true if the method accepts a TokenChargeRecorder parameter
     * @param tokenBudget         token budget for creating per-worker recorders (0 = unlimited)
     */
    public ReflectiveSampleInvoker(Object testInstance,
                            Method testMethod,
                            ExceptionHandling exceptionPolicy,
                            List<Object> inputs,
                            Class<?> inputType,
                            boolean hasTokenRecorderParam,
                            long tokenBudget) {
        this.testInstance = testInstance;
        this.testMethod = testMethod;
        this.exceptionPolicy = exceptionPolicy;
        this.inputs = inputs;
        this.inputType = inputType;
        this.hasTokenRecorderParam = hasTokenRecorderParam;
        this.tokenBudget = tokenBudget;
    }

    @Override
    public StagedResult execute(SampleTask task) {
        Object input = resolveInput(task);
        DefaultTokenChargeRecorder tokenRecorder = hasTokenRecorderParam
                ? new DefaultTokenChargeRecorder(tokenBudget) : null;

        AssertionScope.begin();
        try {
            long startNanos = System.nanoTime();
            invokeTestMethod(input, tokenRecorder);
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            return StagedResult.ofSuccess(
                    task.sequenceIndex(), latencyMs,
                    captureFunctionalResult(),
                    captureLatencyResult(),
                    captureTokens(tokenRecorder));
        } catch (AssertionError e) {
            return StagedResult.ofFailure(
                    task.sequenceIndex(), e,
                    captureFunctionalResult(),
                    captureLatencyResult(),
                    captureTokens(tokenRecorder));
        } catch (Throwable t) {
            if (exceptionPolicy == ExceptionHandling.ABORT_TEST) {
                return StagedResult.ofAbort(task.sequenceIndex(), t);
            }
            return StagedResult.ofFailure(
                    task.sequenceIndex(), t,
                    captureFunctionalResult(),
                    captureLatencyResult(),
                    captureTokens(tokenRecorder));
        } finally {
            AssertionScope.end();
        }
    }

    private Object resolveInput(SampleTask task) {
        if (inputs == null || task.inputIndex() < 0) {
            return null;
        }
        return inputs.get(task.inputIndex());
    }

    private void invokeTestMethod(Object input, DefaultTokenChargeRecorder tokenRecorder) throws Throwable {
        Object[] args = buildArguments(input, tokenRecorder);
        try {
            if (args.length == 0) {
                testMethod.invoke(testInstance);
            } else {
                testMethod.invoke(testInstance, args);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object[] buildArguments(Object input, DefaultTokenChargeRecorder tokenRecorder) {
        java.lang.reflect.Parameter[] params = testMethod.getParameters();
        if (params.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> paramType = params[i].getType();
            if (TokenChargeRecorder.class.isAssignableFrom(paramType) && tokenRecorder != null) {
                args[i] = tokenRecorder;
            } else if (inputType != null && paramType.isAssignableFrom(inputType) && input != null) {
                args[i] = input;
            }
        }
        return args;
    }

    private Boolean captureFunctionalResult() {
        AssertionScope scope = AssertionScope.current();
        if (scope != null && scope.isFunctionalAsserted()) {
            return scope.isFunctionalPassed();
        }
        return null;
    }

    private Boolean captureLatencyResult() {
        AssertionScope scope = AssertionScope.current();
        if (scope != null && scope.isLatencyAsserted()) {
            return scope.isLatencyPassed();
        }
        return null;
    }

    private long captureTokens(DefaultTokenChargeRecorder tokenRecorder) {
        if (tokenRecorder != null) {
            return tokenRecorder.finalizeSample();
        }
        return 0;
    }
}
