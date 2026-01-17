package org.javai.punit.ptest.engine;

import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;

/**
 * Executes individual samples within a probabilistic test.
 *
 * <p>This class encapsulates the core sample execution logic:
 * <ul>
 *   <li>Invoking the test method</li>
 *   <li>Capturing success or failure</li>
 *   <li>Recording results to the aggregator</li>
 *   <li>Applying exception handling policy (ABORT_TEST vs FAIL_SAMPLE)</li>
 * </ul>
 *
 * <p>The executor returns a {@link SampleResult} that allows the caller
 * to determine next steps (continue, abort, finalize).
 *
 * <p>Public to allow access from strategy implementations.
 */
public class SampleExecutor {

    /**
     * Result of executing a single sample.
     *
     * @param passed true if the sample passed
     * @param failure the exception if sample failed, null otherwise
     * @param shouldAbort true if test should abort immediately (ABORT_TEST policy triggered)
     * @param abortException the exception to rethrow if aborting
     */
    public record SampleResult(
            boolean passed,
            Throwable failure,
            boolean shouldAbort,
            Throwable abortException
    ) {
        public static SampleResult ofSuccess() {
            return new SampleResult(true, null, false, null);
        }

        public static SampleResult ofFailure(Throwable failure) {
            return new SampleResult(false, failure, false, null);
        }

        public static SampleResult ofAbort(Throwable failure) {
            return new SampleResult(false, failure, true, failure);
        }

        public boolean hasSampleFailure() {
            return failure != null;
        }
    }

    /**
     * Executes a single sample invocation.
     *
     * <p>The sample is executed within a try/catch that captures:
     * <ul>
     *   <li>{@link AssertionError}: Recorded as failure, test continues</li>
     *   <li>Other {@link Throwable}: Behavior depends on exception policy</li>
     * </ul>
     *
     * @param invocation the JUnit invocation to execute
     * @param aggregator the result aggregator to record to
     * @param exceptionPolicy how to handle non-assertion exceptions
     * @return the result of the sample execution
     * @throws Throwable if the sample should abort and rethrow immediately
     */
    public SampleResult execute(
            Invocation<Void> invocation,
            SampleResultAggregator aggregator,
            ExceptionHandling exceptionPolicy) throws Throwable {

        try {
            invocation.proceed();
            aggregator.recordSuccess();
            return SampleResult.ofSuccess();
        } catch (AssertionError e) {
            aggregator.recordFailure(e);
            return SampleResult.ofFailure(e);
        } catch (Throwable t) {
            aggregator.recordFailure(t);
            
            if (exceptionPolicy == ExceptionHandling.ABORT_TEST) {
                // Signal immediate abort - caller should finalize and rethrow
                return SampleResult.ofAbort(t);
            }
            
            // FAIL_SAMPLE: record and continue
            return SampleResult.ofFailure(t);
        }
    }

    /**
     * Prepares the aggregator for test abort due to exception.
     *
     * <p>This should be called when {@link SampleResult#shouldAbort()} is true,
     * before finalizing the test.
     *
     * @param aggregator the result aggregator
     */
    public void prepareForAbort(SampleResultAggregator aggregator) {
        aggregator.setTerminated(TerminationReason.COMPLETED, "Test aborted due to exception");
    }
}

