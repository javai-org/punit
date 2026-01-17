package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.model.TerminationReason;
import org.javai.punit.ptest.bernoulli.SampleResultAggregator;
import org.javai.punit.ptest.engine.SampleExecutor.SampleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;

/**
 * Tests for {@link SampleExecutor}.
 */
class SampleExecutorTest {

    private SampleExecutor executor;
    private SampleResultAggregator aggregator;

    @BeforeEach
    void setUp() {
        executor = new SampleExecutor();
        aggregator = new SampleResultAggregator(100);
    }

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("records success when invocation completes normally")
        void recordsSuccessOnNormalCompletion() throws Throwable {
            Invocation<Void> successfulInvocation = () -> null;

            SampleResult result = executor.execute(successfulInvocation, aggregator, ExceptionHandling.FAIL_SAMPLE);

            assertThat(result.passed()).isTrue();
            assertThat(result.failure()).isNull();
            assertThat(result.shouldAbort()).isFalse();
            assertThat(result.hasSampleFailure()).isFalse();
            assertThat(aggregator.getSuccesses()).isEqualTo(1);
            assertThat(aggregator.getSamplesExecuted()).isEqualTo(1);
        }

        @Test
        @DisplayName("records failure on AssertionError")
        void recordsFailureOnAssertionError() throws Throwable {
            AssertionError error = new AssertionError("Expected true but was false");
            Invocation<Void> failingInvocation = () -> { throw error; };

            SampleResult result = executor.execute(failingInvocation, aggregator, ExceptionHandling.FAIL_SAMPLE);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(error);
            assertThat(result.shouldAbort()).isFalse();
            assertThat(result.hasSampleFailure()).isTrue();
            assertThat(aggregator.getSuccesses()).isEqualTo(0);
            assertThat(aggregator.getSamplesExecuted()).isEqualTo(1);
        }

        @Test
        @DisplayName("records failure on RuntimeException with FAIL_SAMPLE policy")
        void recordsFailureOnRuntimeExceptionWithFailSamplePolicy() throws Throwable {
            RuntimeException exception = new RuntimeException("Something went wrong");
            Invocation<Void> failingInvocation = () -> { throw exception; };

            SampleResult result = executor.execute(failingInvocation, aggregator, ExceptionHandling.FAIL_SAMPLE);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(exception);
            assertThat(result.shouldAbort()).isFalse();
            assertThat(aggregator.getSamplesExecuted()).isEqualTo(1);
        }

        @Test
        @DisplayName("signals abort on RuntimeException with ABORT_TEST policy")
        void signalsAbortOnRuntimeExceptionWithAbortPolicy() throws Throwable {
            RuntimeException exception = new RuntimeException("Critical failure");
            Invocation<Void> failingInvocation = () -> { throw exception; };

            SampleResult result = executor.execute(failingInvocation, aggregator, ExceptionHandling.ABORT_TEST);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(exception);
            assertThat(result.shouldAbort()).isTrue();
            assertThat(result.abortException()).isSameAs(exception);
            assertThat(aggregator.getSamplesExecuted()).isEqualTo(1);
        }

        @Test
        @DisplayName("does not signal abort on AssertionError even with ABORT_TEST policy")
        void doesNotAbortOnAssertionErrorWithAbortPolicy() throws Throwable {
            AssertionError error = new AssertionError("Assertion failed");
            Invocation<Void> failingInvocation = () -> { throw error; };

            SampleResult result = executor.execute(failingInvocation, aggregator, ExceptionHandling.ABORT_TEST);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(error);
            assertThat(result.shouldAbort()).isFalse(); // AssertionError is a test failure, not an abort
        }

        @Test
        @DisplayName("handles checked exceptions with FAIL_SAMPLE policy")
        void handlesCheckedExceptionWithFailSamplePolicy() throws Throwable {
            Exception checkedException = new Exception("Checked exception");
            Invocation<Void> failingInvocation = () -> { throw checkedException; };

            SampleResult result = executor.execute(failingInvocation, aggregator, ExceptionHandling.FAIL_SAMPLE);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(checkedException);
            assertThat(result.shouldAbort()).isFalse();
        }

        @Test
        @DisplayName("handles checked exceptions with ABORT_TEST policy")
        void handlesCheckedExceptionWithAbortPolicy() throws Throwable {
            Exception checkedException = new Exception("Checked exception requiring abort");
            Invocation<Void> failingInvocation = () -> { throw checkedException; };

            SampleResult result = executor.execute(failingInvocation, aggregator, ExceptionHandling.ABORT_TEST);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(checkedException);
            assertThat(result.shouldAbort()).isTrue();
            assertThat(result.abortException()).isSameAs(checkedException);
        }

        @Test
        @DisplayName("handles Error with ABORT_TEST policy")
        void handlesErrorWithAbortPolicy() throws Throwable {
            OutOfMemoryError oom = new OutOfMemoryError("Heap exhausted");
            Invocation<Void> failingInvocation = () -> { throw oom; };

            SampleResult result = executor.execute(failingInvocation, aggregator, ExceptionHandling.ABORT_TEST);

            assertThat(result.passed()).isFalse();
            assertThat(result.shouldAbort()).isTrue();
            assertThat(result.abortException()).isSameAs(oom);
        }

        @Test
        @DisplayName("accumulates results across multiple samples")
        void accumulatesResults() throws Throwable {
            // Execute 3 successful samples
            for (int i = 0; i < 3; i++) {
                executor.execute(() -> null, aggregator, ExceptionHandling.FAIL_SAMPLE);
            }
            
            // Execute 2 failing samples
            for (int i = 0; i < 2; i++) {
                executor.execute(() -> { throw new AssertionError("fail"); }, 
                        aggregator, ExceptionHandling.FAIL_SAMPLE);
            }

            assertThat(aggregator.getSamplesExecuted()).isEqualTo(5);
            assertThat(aggregator.getSuccesses()).isEqualTo(3);
            assertThat(aggregator.getObservedPassRate()).isEqualTo(0.6);
        }
    }

    @Nested
    @DisplayName("prepareForAbort()")
    class PrepareForAbort {

        @Test
        @DisplayName("sets termination reason on aggregator")
        void setsTerminationReason() {
            executor.prepareForAbort(aggregator);

            assertThat(aggregator.getTerminationReason())
                    .isPresent()
                    .contains(TerminationReason.COMPLETED);
            assertThat(aggregator.getTerminationDetails())
                    .contains("aborted due to exception");
        }
    }

    @Nested
    @DisplayName("SampleResult")
    class SampleResultTests {

        @Test
        @DisplayName("ofSuccess() creates successful result")
        void ofSuccessCreatesSuccessfulResult() {
            SampleResult result = SampleResult.ofSuccess();

            assertThat(result.passed()).isTrue();
            assertThat(result.failure()).isNull();
            assertThat(result.shouldAbort()).isFalse();
            assertThat(result.hasSampleFailure()).isFalse();
        }

        @Test
        @DisplayName("ofFailure() creates failed result")
        void ofFailureCreatesFailedResult() {
            Throwable error = new AssertionError("test");
            SampleResult result = SampleResult.ofFailure(error);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(error);
            assertThat(result.shouldAbort()).isFalse();
            assertThat(result.hasSampleFailure()).isTrue();
        }

        @Test
        @DisplayName("ofAbort() creates abort result")
        void ofAbortCreatesAbortResult() {
            Throwable error = new RuntimeException("critical");
            SampleResult result = SampleResult.ofAbort(error);

            assertThat(result.passed()).isFalse();
            assertThat(result.failure()).isSameAs(error);
            assertThat(result.shouldAbort()).isTrue();
            assertThat(result.abortException()).isSameAs(error);
            assertThat(result.hasSampleFailure()).isTrue();
        }
    }
}

