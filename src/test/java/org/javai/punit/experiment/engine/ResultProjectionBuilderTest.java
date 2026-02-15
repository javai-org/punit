package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.javai.punit.contract.PostconditionEvaluator;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.model.ResultProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResultProjectionBuilder")
class ResultProjectionBuilderTest {

    private static final PostconditionEvaluator<Object> ALWAYS_PASSING = new PostconditionEvaluator<>() {
        @Override
        public List<PostconditionResult> evaluate(Object result) {
            return List.of();
        }

        @Override
        public int postconditionCount() {
            return 0;
        }
    };

    @SuppressWarnings("unchecked")
    private static <T> PostconditionEvaluator<T> alwaysPassing() {
        return (PostconditionEvaluator<T>) ALWAYS_PASSING;
    }

    record TestResult(String key, String value) {}

    private UseCaseOutcome<TestResult> createOutcome(String key, String value) {
        return createOutcome(new TestResult(key, value), Duration.ZERO);
    }

    private UseCaseOutcome<TestResult> createOutcome(TestResult result, Duration executionTime) {
        return new UseCaseOutcome<>(
            result,
            executionTime,
            Instant.now(),
            Map.of(),
            alwaysPassing(),
            null,
            null,
            null
        );
    }

    @Nested
    @DisplayName("build()")
    class Build {

        @Test
        @DisplayName("creates projection with correct sample index")
        void createsProjectionWithCorrectSampleIndex() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            UseCaseOutcome<TestResult> outcome = createOutcome("key", "value");

            ResultProjection projection = builder.build(3, outcome);

            assertThat(projection.sampleIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("captures execution time in milliseconds")
        void capturesExecutionTimeInMilliseconds() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            UseCaseOutcome<TestResult> outcome = createOutcome(
                new TestResult("key", "value"),
                Duration.ofMillis(250)
            );

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.executionTimeMs()).isEqualTo(250);
        }

        @Test
        @DisplayName("uses toString() for content")
        void usesToStringForContent() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            UseCaseOutcome<TestResult> outcome = createOutcome("myKey", "myValue");

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.content()).isEqualTo("TestResult[key=myKey, value=myValue]");
        }

        @Test
        @DisplayName("returns null content for null result")
        void returnsNullContentForNullResult() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            UseCaseOutcome<TestResult> outcome = new UseCaseOutcome<>(
                null,
                Duration.ZERO,
                Instant.now(),
                Map.of(),
                alwaysPassing(),
                null,
                null,
                null
            );

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.content()).isNull();
        }

        @Test
        @DisplayName("failureDetail is null for successful outcomes")
        void failureDetailIsNullForSuccessfulOutcomes() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            UseCaseOutcome<TestResult> outcome = createOutcome("key", "value");

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.failureDetail()).isNull();
        }

        @Test
        @DisplayName("preserves full content without truncation")
        void preservesFullContentWithoutTruncation() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            String longValue = "x".repeat(500);
            UseCaseOutcome<TestResult> outcome = createOutcome("key", longValue);

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.content()).contains(longValue);
        }
    }

    @Nested
    @DisplayName("buildError()")
    class BuildError {

        @Test
        @DisplayName("includes error class and message in failureDetail")
        void includesErrorClassAndMessageInFailureDetail() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();

            ResultProjection projection = builder.buildError(
                0, "test input", 100,
                new IllegalArgumentException("test message")
            );

            assertThat(projection.failureDetail())
                .isEqualTo("IllegalArgumentException: test message");
        }

        @Test
        @DisplayName("content is null for error outcomes")
        void contentIsNullForErrorOutcomes() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();

            ResultProjection projection = builder.buildError(
                0, null, 100, new RuntimeException("error")
            );

            assertThat(projection.content()).isNull();
        }

        @Test
        @DisplayName("handles multi-line error messages")
        void handlesMultilineErrorMessages() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();

            ResultProjection projection = builder.buildError(
                0, "test input", 100,
                new RuntimeException("first line\nsecond line\nthird line")
            );

            assertThat(projection.failureDetail())
                .isEqualTo("RuntimeException: first line");
        }

        @Test
        @DisplayName("handles null error message")
        void handlesNullErrorMessage() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();

            ResultProjection projection = builder.buildError(
                0, null, 100,
                new RuntimeException((String) null)
            );

            assertThat(projection.failureDetail())
                .isEqualTo("RuntimeException: null");
        }

        @Test
        @DisplayName("captures input in error projection")
        void capturesInputInErrorProjection() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();

            ResultProjection projection = builder.buildError(
                0, "Add 2 apples", 100,
                new RuntimeException("error")
            );

            assertThat(projection.input()).isEqualTo("Add 2 apples");
            assertThat(projection.success()).isFalse();
        }

        @Test
        @DisplayName("marks execution completed as failed")
        void marksExecutionCompletedAsFailed() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder();

            ResultProjection projection = builder.buildError(0, null, 100, new RuntimeException("error"));

            assertThat(projection.postconditions())
                .containsEntry("Execution completed", ResultProjection.FAILED);
            assertThat(projection.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("postconditions extraction")
    class PostconditionsExtraction {

        @Test
        @DisplayName("extracts postcondition results as map")
        void extractsPostconditionResultsAsMap() {
            PostconditionEvaluator<TestResult> evaluator = new PostconditionEvaluator<>() {
                @Override
                public List<PostconditionResult> evaluate(TestResult result) {
                    return List.of(
                        PostconditionResult.passed("Response not empty"),
                        PostconditionResult.failed("Valid JSON", "Parse error")
                    );
                }

                @Override
                public int postconditionCount() {
                    return 2;
                }
            };

            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            UseCaseOutcome<TestResult> outcome = new UseCaseOutcome<>(
                new TestResult("key", "value"),
                Duration.ZERO,
                Instant.now(),
                Map.of(),
                evaluator,
                null,
                null,
                null
            );

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.postconditions())
                .containsEntry("Response not empty", ResultProjection.PASSED)
                .containsEntry("Valid JSON", ResultProjection.FAILED);
            assertThat(projection.success()).isFalse();
        }

        @Test
        @DisplayName("success is true when all postconditions pass")
        void successIsTrueWhenAllPass() {
            PostconditionEvaluator<TestResult> evaluator = new PostconditionEvaluator<>() {
                @Override
                public List<PostconditionResult> evaluate(TestResult result) {
                    return List.of(
                        PostconditionResult.passed("Check 1"),
                        PostconditionResult.passed("Check 2")
                    );
                }

                @Override
                public int postconditionCount() {
                    return 2;
                }
            };

            ResultProjectionBuilder builder = new ResultProjectionBuilder();
            UseCaseOutcome<TestResult> outcome = new UseCaseOutcome<>(
                new TestResult("key", "value"),
                Duration.ZERO,
                Instant.now(),
                Map.of(),
                evaluator,
                null,
                null,
                null
            );

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.success()).isTrue();
        }
    }
}
