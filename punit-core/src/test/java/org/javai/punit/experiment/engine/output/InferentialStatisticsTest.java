package org.javai.punit.experiment.engine.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.javai.punit.experiment.engine.YamlBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InferentialStatistics")
class InferentialStatisticsTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("of() creates stats without failure distribution")
        void ofCreatesStatsWithoutFailureDistribution() {
            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100
            );

            assertThat(stats.observed()).isEqualTo(0.90);
            assertThat(stats.standardError()).isEqualTo(0.0095);
            assertThat(stats.ciLower()).isEqualTo(0.8814);
            assertThat(stats.ciUpper()).isEqualTo(0.9186);
            assertThat(stats.successes()).isEqualTo(900);
            assertThat(stats.failures()).isEqualTo(100);
            assertThat(stats.failureDistribution()).isEmpty();
        }

        @Test
        @DisplayName("of() with distribution creates stats with failure distribution")
        void ofWithDistributionCreatesStatsWithFailureDistribution() {
            Map<String, Integer> dist = Map.of("TIMEOUT", 50, "ERROR", 50);

            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100, dist
            );

            assertThat(stats.failureDistribution()).containsAllEntriesOf(dist);
        }

        @Test
        @DisplayName("sampleCount() returns sum of successes and failures")
        void sampleCountReturnsSumOfSuccessesAndFailures() {
            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100
            );

            assertThat(stats.sampleCount()).isEqualTo(1000);
        }

        @Test
        @DisplayName("derivedMinPassRate() returns CI lower bound")
        void derivedMinPassRateReturnsCiLowerBound() {
            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100
            );

            assertThat(stats.derivedMinPassRate()).isEqualTo(0.8814);
        }
    }

    @Nested
    @DisplayName("writeTo()")
    class WriteTo {

        @Test
        @DisplayName("writes full statistics section with nested successRate")
        void writesFullStatisticsSectionWithNestedSuccessRate() {
            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100
            );
            YamlBuilder builder = YamlBuilder.create();

            stats.writeTo(builder);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("statistics:")
                .contains("successRate:")
                .contains("observed: 0.9000")
                .contains("standardError: 0.0095")
                .contains("confidenceInterval95: [0.8814, 0.9186]")
                .contains("successes: 900")
                .contains("failures: 100");
        }

        @Test
        @DisplayName("writes statistics section with failure distribution")
        void writesStatisticsSectionWithFailureDistribution() {
            Map<String, Integer> dist = new LinkedHashMap<>();
            dist.put("TIMEOUT", 50);
            dist.put("VALIDATION_ERROR", 50);
            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100, dist
            );
            YamlBuilder builder = YamlBuilder.create();

            stats.writeTo(builder);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("failureDistribution:")
                .contains("TIMEOUT: 50")
                .contains("VALIDATION_ERROR: 50");
        }

        @Test
        @DisplayName("includes all inferential statistics")
        void includesAllInferentialStatistics() {
            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100
            );
            YamlBuilder builder = YamlBuilder.create();

            stats.writeTo(builder);
            String yaml = builder.build();

            // These are the key inferential stats that EXPLORE/OPTIMIZE omit
            assertThat(yaml)
                .contains("standardError")
                .contains("confidenceInterval95");
        }
    }

    @Nested
    @DisplayName("writeRequirementsTo()")
    class WriteRequirementsTo {

        @Test
        @DisplayName("writes requirements section with minPassRate from CI lower")
        void writesRequirementsSectionWithMinPassRateFromCiLower() {
            InferentialStatistics stats = InferentialStatistics.of(
                0.90, 0.0095, 0.8814, 0.9186, 900, 100
            );
            YamlBuilder builder = YamlBuilder.create();

            stats.writeRequirementsTo(builder);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("requirements:")
                .contains("minPassRate: 0.8814");
        }
    }
}
