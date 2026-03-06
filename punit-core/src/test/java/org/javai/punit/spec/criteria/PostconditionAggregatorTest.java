package org.javai.punit.spec.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.javai.punit.contract.PostconditionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PostconditionAggregator")
class PostconditionAggregatorTest {

    private PostconditionAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new PostconditionAggregator();
    }

    @Nested
    @DisplayName("record()")
    class Record {

        @Test
        @DisplayName("should track samples recorded")
        void shouldTrackSamplesRecorded() {
            List<PostconditionResult> results = List.of(
                PostconditionResult.passed("Test")
            );

            aggregator.record(results);
            aggregator.record(results);
            aggregator.record(results);

            assertThat(aggregator.getSamplesRecorded()).isEqualTo(3);
        }

        @Test
        @DisplayName("should count all-passed samples")
        void shouldCountAllPassedSamples() {
            List<PostconditionResult> allPass = List.of(
                PostconditionResult.passed("A"),
                PostconditionResult.passed("B")
            );

            List<PostconditionResult> someFail = List.of(
                PostconditionResult.passed("A"),
                PostconditionResult.failed("B", "Condition not met")
            );

            aggregator.record(allPass);
            aggregator.record(allPass);
            aggregator.record(someFail);

            assertThat(aggregator.getAllPassedCount()).isEqualTo(2);
            assertThat(aggregator.getSamplesRecorded()).isEqualTo(3);
        }

        @Test
        @DisplayName("should preserve postcondition order")
        void shouldPreservePostconditionOrder() {
            List<PostconditionResult> results = List.of(
                PostconditionResult.passed("First"),
                PostconditionResult.passed("Second"),
                PostconditionResult.passed("Third")
            );

            aggregator.record(results);

            Map<String, PostconditionAggregator.PostconditionStats> stats =
                aggregator.getPostconditionStats();
            String[] keys = stats.keySet().toArray(new String[0]);

            assertThat(keys).containsExactly("First", "Second", "Third");
        }

        @Test
        @DisplayName("should throw on null results")
        void shouldThrowOnNullResults() {
            assertThatThrownBy(() -> aggregator.record((List<PostconditionResult>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("results must not be null");
        }
    }

    @Nested
    @DisplayName("PostconditionStats")
    class PostconditionStatsTests {

        @Test
        @DisplayName("should track passed count")
        void shouldTrackPassedCount() {
            List<PostconditionResult> passing = List.of(
                PostconditionResult.passed("Test")
            );
            List<PostconditionResult> failing = List.of(
                PostconditionResult.failed("Test", "Not satisfied")
            );

            aggregator.record(passing);
            aggregator.record(passing);
            aggregator.record(failing);

            PostconditionAggregator.PostconditionStats stats =
                aggregator.getPostconditionStats().get("Test");

            assertThat(stats.getPassed()).isEqualTo(2);
            assertThat(stats.getFailed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should calculate pass rate")
        void shouldCalculatePassRate() {
            List<PostconditionResult> passing = List.of(
                PostconditionResult.passed("Test")
            );
            List<PostconditionResult> failing = List.of(
                PostconditionResult.failed("Test", "Not satisfied")
            );

            aggregator.record(passing);
            aggregator.record(passing);
            aggregator.record(passing);
            aggregator.record(failing);

            PostconditionAggregator.PostconditionStats stats =
                aggregator.getPostconditionStats().get("Test");

            assertThat(stats.getPassRate()).isCloseTo(0.75,
                org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("should track skipped outcomes")
        void shouldTrackSkippedOutcomes() {
            List<PostconditionResult> skipped = List.of(
                PostconditionResult.failed("Test", "Skipped: Derivation failed")
            );

            aggregator.record(skipped);
            aggregator.record(skipped);

            PostconditionAggregator.PostconditionStats stats =
                aggregator.getPostconditionStats().get("Test");

            assertThat(stats.getSkipped()).isEqualTo(2);
            assertThat(stats.getFailed()).isZero();
            assertThat(stats.getPassed()).isZero();
        }

        @Test
        @DisplayName("should track failure messages")
        void shouldTrackFailureMessages() {
            List<PostconditionResult> failing = List.of(
                PostconditionResult.failed("Test", "Error message")
            );

            aggregator.record(failing);
            aggregator.record(failing);

            PostconditionAggregator.PostconditionStats stats =
                aggregator.getPostconditionStats().get("Test");

            assertThat(stats.getFailureMessages()).hasSize(2);
            assertThat(stats.getFailureMessages()).allMatch(m -> m.equals("Error message"));
        }

        @Test
        @DisplayName("should limit failure message collection")
        void shouldLimitFailureMessages() {
            List<PostconditionResult> failing = List.of(
                PostconditionResult.failed("Test", "Error")
            );

            for (int i = 0; i < 10; i++) {
                aggregator.record(failing);
            }

            PostconditionAggregator.PostconditionStats stats =
                aggregator.getPostconditionStats().get("Test");

            assertThat(stats.getFailed()).isEqualTo(10);
            assertThat(stats.getFailureMessages()).hasSize(5); // Capped at 5
        }
    }

    @Nested
    @DisplayName("getOverallSuccessRate()")
    class OverallSuccessRate {

        @Test
        @DisplayName("should return 0 for no samples")
        void shouldReturnZeroForNoSamples() {
            assertThat(aggregator.getOverallSuccessRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should calculate correct rate")
        void shouldCalculateCorrectRate() {
            List<PostconditionResult> allPass = List.of(
                PostconditionResult.passed("A"),
                PostconditionResult.passed("B")
            );

            List<PostconditionResult> someFail = List.of(
                PostconditionResult.passed("A"),
                PostconditionResult.failed("B", "Not satisfied")
            );

            // 2 all-pass, 2 with failures
            aggregator.record(allPass);
            aggregator.record(allPass);
            aggregator.record(someFail);
            aggregator.record(someFail);

            assertThat(aggregator.getOverallSuccessRate()).isCloseTo(0.5,
                org.assertj.core.data.Offset.offset(0.001));
        }
    }

    @Nested
    @DisplayName("getPassRateSummary()")
    class PassRateSummary {

        @Test
        @DisplayName("should return map of descriptions to pass rates")
        void shouldReturnPassRateMap() {
            for (int i = 0; i < 100; i++) {
                aggregator.record(List.of(
                    PostconditionResult.passed("Always passes"),
                    PostconditionResult.failed("Never passes", "Fails")
                ));
            }

            Map<String, Double> summary = aggregator.getPassRateSummary();

            assertThat(summary.get("Always passes")).isEqualTo(1.0);
            assertThat(summary.get("Never passes")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("mixed outcomes")
    class MixedOutcomes {

        @Test
        @DisplayName("should handle mix of passed, failed, and skipped")
        void shouldHandleMixedOutcomes() {
            aggregator.record(List.of(
                PostconditionResult.passed("Test"),
                PostconditionResult.passed("Another")
            ));
            aggregator.record(List.of(
                PostconditionResult.failed("Test", "Not satisfied"),
                PostconditionResult.passed("Another")
            ));
            aggregator.record(List.of(
                PostconditionResult.failed("Test", "Skipped: Derivation failed"),
                PostconditionResult.failed("Another", "Skipped: Derivation failed")
            ));

            PostconditionAggregator.PostconditionStats testStats =
                aggregator.getPostconditionStats().get("Test");
            PostconditionAggregator.PostconditionStats anotherStats =
                aggregator.getPostconditionStats().get("Another");

            assertThat(testStats.getPassed()).isEqualTo(1);
            assertThat(testStats.getFailed()).isEqualTo(1);
            assertThat(testStats.getSkipped()).isEqualTo(1);
            assertThat(testStats.getTotal()).isEqualTo(3);

            assertThat(anotherStats.getPassed()).isEqualTo(2);
            assertThat(anotherStats.getFailed()).isZero();
            assertThat(anotherStats.getSkipped()).isEqualTo(1);
        }
    }
}
