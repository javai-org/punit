package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.javai.punit.api.DiffableContentProvider;
import org.javai.punit.contract.PostconditionEvaluator;
import org.javai.punit.contract.PostconditionResult;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.punit.experiment.model.ResultProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ResultProjectionBuilder")
class ResultProjectionBuilderTest {

    // Simple no-op evaluator for tests
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

    // Helper to create a UseCaseOutcome with a record result
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

    // Test record for outcomes
    record TestResult(String key, String value) {}

    // Multi-field test record
    record MultiFieldResult(String alpha, String beta, String gamma, String delta, String epsilon) {}

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("rejects maxDiffableLines less than 1")
        void rejectsMaxDiffableLinesLessThan1() {
            assertThatThrownBy(() -> new ResultProjectionBuilder(0, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDiffableLines must be at least 1");
        }

        @Test
        @DisplayName("rejects maxLineLength less than 10")
        void rejectsMaxLineLengthLessThan10() {
            assertThatThrownBy(() -> new ResultProjectionBuilder(5, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLineLength must be at least 10");
        }
    }

    @Nested
    @DisplayName("build()")
    class Build {

        @Test
        @DisplayName("creates projection with correct sample index")
        void createsProjectionWithCorrectSampleIndex() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseOutcome<TestResult> outcome = createOutcome("key", "value");

            ResultProjection projection = builder.build(3, outcome);

            assertThat(projection.sampleIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("captures execution time in milliseconds")
        void capturesExecutionTimeInMilliseconds() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseOutcome<TestResult> outcome = createOutcome(
                new TestResult("key", "value"),
                Duration.ofMillis(250)
            );

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.executionTimeMs()).isEqualTo(250);
        }

        @Test
        @DisplayName("extracts record components alphabetically")
        void extractsRecordComponentsAlphabetically() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseOutcome<TestResult> outcome = createOutcome("key", "value");

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.diffableLines()).contains("key: key", "value: value");
        }
    }

    @Nested
    @DisplayName("line count normalization")
    class LineCountNormalization {

        @Test
        @DisplayName("pads with <absent> when fewer values than max")
        void padsWithAbsentWhenFewerValues() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
            UseCaseOutcome<TestResult> outcome = createOutcome("only", "one");

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.diffableLines()).hasSize(5);
            // Record has 2 fields: key and value
            assertThat(projection.diffableLines().get(0)).isEqualTo("key: only");
            assertThat(projection.diffableLines().get(1)).isEqualTo("value: one");
            assertThat(projection.diffableLines().get(2)).isEqualTo(ResultProjection.ABSENT);
            assertThat(projection.diffableLines().get(4)).isEqualTo(ResultProjection.ABSENT);
        }

        @Test
        @DisplayName("adds truncation notice when more values than max")
        void addsTruncationNoticeWhenMoreValues() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(3, 60);
            // Use a record with 5 fields
            UseCaseOutcome<MultiFieldResult> outcome = new UseCaseOutcome<>(
                new MultiFieldResult("1", "2", "3", "4", "5"),
                Duration.ZERO,
                Instant.now(),
                Map.of(),
                alwaysPassing(),
                null,
                null,
                null
            );

            ResultProjection projection = builder.build(0, outcome);

            // Should have 3 content lines + 1 truncation notice = 4 total
            assertThat(projection.diffableLines()).hasSize(4);
            // Alphabetically: alpha, beta, delta, epsilon, gamma
            assertThat(projection.diffableLines().get(0)).isEqualTo("alpha: 1");
            assertThat(projection.diffableLines().get(1)).isEqualTo("beta: 2");
            assertThat(projection.diffableLines().get(2)).isEqualTo("delta: 4");
            assertThat(projection.diffableLines().get(3)).isEqualTo("<truncated: +2 more>");
        }

        @Test
        @DisplayName("no padding or truncation when exactly max values")
        void noPaddingOrTruncationWhenExactlyMaxValues() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(2, 60);
            UseCaseOutcome<TestResult> outcome = createOutcome("a", "b");

            ResultProjection projection = builder.build(0, outcome);

            // Record has exactly 2 fields (key, value)
            assertThat(projection.diffableLines()).hasSize(2);
            assertThat(projection.diffableLines()).noneMatch(line ->
                line.equals(ResultProjection.ABSENT) || line.startsWith("<truncated"));
        }
    }

    @Nested
    @DisplayName("buildError()")
    class BuildError {

        @Test
        @DisplayName("includes error class name")
        void includesErrorClassName() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);

            ResultProjection projection = builder.buildError(
                0,
                "test input",
                100,
                new IllegalArgumentException("test message")
            );

            assertThat(projection.diffableLines().get(0))
                .isEqualTo("error: IllegalArgumentException");
        }

        @Test
        @DisplayName("includes error message")
        void includesErrorMessage() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);

            ResultProjection projection = builder.buildError(
                0,
                "test input",
                100,
                new IllegalArgumentException("test message")
            );

            assertThat(projection.diffableLines().get(1))
                .isEqualTo("message: test message");
        }

        @Test
        @DisplayName("pads remaining lines with absent")
        void padsRemainingLinesWithAbsent() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);

            ResultProjection projection = builder.buildError(
                0,
                null,
                100,
                new RuntimeException("error")
            );

            // 2 content lines (error, message) + 3 absent = 5 total
            assertThat(projection.diffableLines()).hasSize(5);
            assertThat(projection.diffableLines().get(2)).isEqualTo(ResultProjection.ABSENT);
        }

        @Test
        @DisplayName("handles multi-line error messages")
        void handlesMultilineErrorMessages() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);

            ResultProjection projection = builder.buildError(
                0,
                "test input",
                100,
                new RuntimeException("first line\nsecond line\nthird line")
            );

            // Should only show first line
            assertThat(projection.diffableLines().get(1))
                .isEqualTo("message: first line");
        }

        @Test
        @DisplayName("handles null error message")
        void handlesNullErrorMessage() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);

            ResultProjection projection = builder.buildError(
                0,
                null,
                100,
                new RuntimeException((String) null)
            );

            assertThat(projection.diffableLines().get(1))
                .isEqualTo("message: null");
        }

        @Test
        @DisplayName("captures input in error projection")
        void capturesInputInErrorProjection() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);

            ResultProjection projection = builder.buildError(
                0,
                "Add 2 apples",
                100,
                new RuntimeException("error")
            );

            assertThat(projection.input()).isEqualTo("Add 2 apples");
            assertThat(projection.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("custom provider")
    class CustomProvider {

        @Test
        @DisplayName("uses custom provider when supplied")
        void usesCustomProviderWhenSupplied() {
            DiffableContentProvider customProvider = (outcome, maxLineLength) ->
                List.of("custom: projection");

            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60, customProvider);
            UseCaseOutcome<TestResult> outcome = createOutcome("ignored", "value");

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.diffableLines().get(0)).isEqualTo("custom: projection");
        }

        @Test
        @DisplayName("pads custom provider output if needed")
        void padsCustomProviderOutputIfNeeded() {
            DiffableContentProvider customProvider = (outcome, maxLineLength) ->
                List.of("line1", "line2");

            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60, customProvider);
            UseCaseOutcome<TestResult> outcome = createOutcome("ignored", "value");

            ResultProjection projection = builder.build(0, outcome);

            assertThat(projection.diffableLines()).hasSize(5);
            assertThat(projection.diffableLines().get(2)).isEqualTo(ResultProjection.ABSENT);
        }

        @Test
        @DisplayName("truncates custom provider output if exceeds max")
        void truncatesCustomProviderOutputIfExceedsMax() {
            DiffableContentProvider customProvider = (outcome, maxLineLength) ->
                List.of("a", "b", "c", "d", "e", "f", "g");

            ResultProjectionBuilder builder = new ResultProjectionBuilder(3, 60, customProvider);
            UseCaseOutcome<TestResult> outcome = createOutcome("ignored", "value");

            ResultProjection projection = builder.build(0, outcome);

            // 3 content + 1 truncation = 4 total
            assertThat(projection.diffableLines()).hasSize(4);
            assertThat(projection.diffableLines().get(3)).isEqualTo("<truncated: +4 more>");
        }
    }

    @Nested
    @DisplayName("null result handling")
    class NullResultHandling {

        @Test
        @DisplayName("handles null result")
        void handlesNullResult() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
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

            assertThat(projection.diffableLines().get(0)).isEqualTo("result: null");
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

            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
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

            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);
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

        @Test
        @DisplayName("error projection has execution completed failed")
        void errorProjectionHasExecutionCompletedFailed() {
            ResultProjectionBuilder builder = new ResultProjectionBuilder(5, 60);

            ResultProjection projection = builder.buildError(0, null, 100, new RuntimeException("error"));

            assertThat(projection.postconditions())
                .containsEntry("Execution completed", ResultProjection.FAILED);
            assertThat(projection.success()).isFalse();
        }
    }
}
