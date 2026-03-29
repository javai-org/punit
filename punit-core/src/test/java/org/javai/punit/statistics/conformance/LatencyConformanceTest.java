package org.javai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javai.punit.statistics.LatencyStatistics;
import org.javai.punit.statistics.LatencyThresholdDeriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Conformance tests for latency statistics: empirical percentile estimation,
 * summary statistics, and threshold derivation from baseline data.
 *
 * @see <a href="https://github.com/javai-org/javai-R">javai-R</a>
 */
@DisplayName("Latency conformance (javai-R)")
class LatencyConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFORMANCE_DIR = "/conformance/";

    private static JsonNode loadSuite(String filename) {
        try (InputStream is = LatencyConformanceTest.class.getResourceAsStream(CONFORMANCE_DIR + filename)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Conformance data not found: " + CONFORMANCE_DIR + filename
                                + ". Run the Gradle downloadConformanceData task or check the checked-in fallback files.");
            }
            return MAPPER.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read conformance suite: " + filename, e);
        }
    }

    private static double[] toDoubleArray(JsonNode node) {
        if (node.isArray()) {
            double[] values = new double[node.size()];
            for (int i = 0; i < node.size(); i++) {
                values[i] = node.get(i).asDouble();
            }
            return values;
        }
        // Single scalar value
        return new double[]{ node.asDouble() };
    }

    @Nested
    @DisplayName("latency_percentile — nearest-rank percentile")
    class Percentile {

        @TestFactory
        @DisplayName("Nearest-rank (ceiling) percentile estimation")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_percentile.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                var inputs = c.get("inputs");
                if (!inputs.has("percentile")) {
                    continue; // summary case, handled by the Summary nested class
                }

                String name = c.get("name").asText();
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] latencies = toDoubleArray(inputs.get("latencies"));
                    double p = inputs.get("percentile").asDouble();

                    double result = LatencyStatistics.nearestRankPercentile(latencies, p);

                    assertThat(result)
                            .as("percentile value")
                            .isCloseTo(expected.get("value").asDouble(), within(tolerance));
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("latency_percentile — summary statistics")
    class Summary {

        @TestFactory
        @DisplayName("Mean, sample standard deviation, and maximum")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_percentile.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                var inputs = c.get("inputs");
                if (inputs.has("percentile")) {
                    continue; // percentile case, handled by the Percentile nested class
                }

                String name = c.get("name").asText();
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] latencies = toDoubleArray(inputs.get("latencies"));

                    assertThat(LatencyStatistics.mean(latencies))
                            .as("mean")
                            .isCloseTo(expected.get("mean").asDouble(), within(tolerance));

                    assertThat(LatencyStatistics.max(latencies))
                            .as("max")
                            .isCloseTo(expected.get("max").asDouble(), within(tolerance));

                    OptionalDouble sd = LatencyStatistics.sampleStandardDeviation(latencies);
                    if (expected.get("sd").isNull()) {
                        assertThat(sd)
                                .as("sd (undefined for single observation)")
                                .isEmpty();
                    } else {
                        assertThat(sd).as("sd present").isPresent();
                        assertThat(sd.getAsDouble())
                                .as("sd")
                                .isCloseTo(expected.get("sd").asDouble(), within(tolerance));
                    }
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("latency_threshold")
    class Threshold {

        @TestFactory
        @DisplayName("One-sided upper confidence bound for latency thresholds")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_threshold.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double baselinePercentile = inputs.get("baseline_percentile").asDouble();
                    double baselineSd = inputs.get("baseline_sd").asDouble();
                    int baselineN = inputs.get("baseline_n").asInt();
                    double confidence = inputs.get("confidence").asDouble();

                    LatencyThresholdDeriver.UpperBound result = LatencyThresholdDeriver.derive(
                            baselinePercentile, baselineSd, baselineN, confidence);

                    assertThat(result.rawUpperBound())
                            .as("raw upper bound")
                            .isCloseTo(expected.get("raw_upper").asDouble(), within(tolerance));
                    assertThat(result.threshold())
                            .as("threshold")
                            .isEqualTo(expected.get("threshold").asLong());
                }));
            }
            return tests;
        }
    }
}
