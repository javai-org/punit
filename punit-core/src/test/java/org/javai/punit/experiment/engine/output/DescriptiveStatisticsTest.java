package org.javai.punit.experiment.engine.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.javai.punit.experiment.engine.YamlBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DescriptiveStatistics")
class DescriptiveStatisticsTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("of() creates stats without failure distribution")
        void ofCreatesStatsWithoutFailureDistribution() {
            DescriptiveStatistics stats = DescriptiveStatistics.of(0.75, 15, 5);

            assertThat(stats.observed()).isEqualTo(0.75);
            assertThat(stats.successes()).isEqualTo(15);
            assertThat(stats.failures()).isEqualTo(5);
            assertThat(stats.failureDistribution()).isEmpty();
        }

        @Test
        @DisplayName("of() with distribution creates stats with failure distribution")
        void ofWithDistributionCreatesStatsWithFailureDistribution() {
            Map<String, Integer> dist = Map.of("TIMEOUT", 3, "ERROR", 2);

            DescriptiveStatistics stats = DescriptiveStatistics.of(0.75, 15, 5, dist);

            assertThat(stats.failureDistribution()).containsAllEntriesOf(dist);
        }

        @Test
        @DisplayName("sampleCount() returns sum of successes and failures")
        void sampleCountReturnsSumOfSuccessesAndFailures() {
            DescriptiveStatistics stats = DescriptiveStatistics.of(0.75, 15, 5);

            assertThat(stats.sampleCount()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("writeTo()")
    class WriteTo {

        @Test
        @DisplayName("writes statistics section without failure distribution")
        void writesStatisticsSectionWithoutFailureDistribution() {
            DescriptiveStatistics stats = DescriptiveStatistics.of(0.70, 14, 6);
            YamlBuilder builder = YamlBuilder.create();

            stats.writeTo(builder);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("statistics:")
                .contains("observed: 0.7000")
                .contains("successes: 14")
                .contains("failures: 6")
                .doesNotContain("failureDistribution");
        }

        @Test
        @DisplayName("writes statistics section with failure distribution")
        void writesStatisticsSectionWithFailureDistribution() {
            Map<String, Integer> dist = new LinkedHashMap<>();
            dist.put("TIMEOUT", 3);
            dist.put("VALIDATION_ERROR", 3);
            DescriptiveStatistics stats = DescriptiveStatistics.of(0.70, 14, 6, dist);
            YamlBuilder builder = YamlBuilder.create();

            stats.writeTo(builder);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("statistics:")
                .contains("observed: 0.7000")
                .contains("failureDistribution:")
                .contains("TIMEOUT: 3")
                .contains("VALIDATION_ERROR: 3");
        }

        @Test
        @DisplayName("does NOT include standard error or confidence interval")
        void doesNotIncludeStandardErrorOrConfidenceInterval() {
            DescriptiveStatistics stats = DescriptiveStatistics.of(0.70, 14, 6);
            YamlBuilder builder = YamlBuilder.create();

            stats.writeTo(builder);
            String yaml = builder.build();

            assertThat(yaml)
                .doesNotContain("standardError")
                .doesNotContain("confidenceInterval");
        }
    }

    @Nested
    @DisplayName("writeCompactTo()")
    class WriteCompactTo {

        @Test
        @DisplayName("writes compact format with sample count")
        void writesCompactFormatWithSampleCount() {
            DescriptiveStatistics stats = DescriptiveStatistics.of(0.75, 15, 5);
            YamlBuilder builder = YamlBuilder.create();

            stats.writeCompactTo(builder);
            String yaml = builder.build();

            assertThat(yaml)
                .contains("statistics:")
                .contains("sampleCount: 20")
                .contains("observed: 0.7500")
                .contains("successes: 15")
                .contains("failures: 5")
                .doesNotContain("failureDistribution");
        }
    }
}
