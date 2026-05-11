package org.javai.punit.statistics.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javai.punit.statistics.LatencyStatistics;
import org.javai.punit.statistics.LatencyThresholdDeriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

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
        @DisplayName("Mean and maximum")
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
                }));
            }
            return tests;
        }
    }

    @Nested
    @DisplayName("latency_threshold")
    class Threshold {

        @TestFactory
        @DisplayName("Exact binomial order-statistic upper bound on the baseline percentile")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_threshold.json");
            double tolerance = suite.get("tolerance").asDouble();

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] baselineLatencies = toDoubleArray(inputs.get("baseline_latencies"));
                    double p = inputs.get("p").asDouble();
                    double confidence = inputs.get("confidence").asDouble();

                    LatencyThresholdDeriver.Threshold result =
                            LatencyThresholdDeriver.derive(baselineLatencies, p, confidence);

                    assertThat(result.rank())
                            .as("rank (k)")
                            .isEqualTo(expected.get("rank").asInt());
                    assertThat(result.threshold())
                            .as("threshold (t_{(k)})")
                            .isCloseTo(expected.get("threshold").asDouble(), within(tolerance));
                    assertThat(result.baselinePercentile())
                            .as("baseline percentile (Q(p))")
                            .isCloseTo(expected.get("baseline_percentile").asDouble(), within(tolerance));
                    assertThat(result.n())
                            .as("n")
                            .isEqualTo(expected.get("n").asInt());
                }));
            }
            return tests;
        }
    }

    /**
     * Conformance against the bootstrap-comparison suite — the one whose
     * `expected` section publishes the binomial fields alongside the
     * informational bootstrap fields. Per the suite's own description, the
     * conformance fields ({@code rank}, {@code threshold},
     * {@code baseline_percentile}, {@code n}) are integer-valued or are
     * specific elements of the integer-valued {@code baseline_latencies}
     * array, so the suite carries {@code tolerance: 0} and we check exact
     * equality. The fields {@code bootstrap_upper}, {@code point_estimate},
     * and {@code diff} are informational and are not asserted as
     * conformance targets here — they are exercised separately by the
     * conservatism sanity check below.
     */
    @Nested
    @DisplayName("latency_threshold_bootstrap (binomial side; bootstrap fields informational)")
    class BootstrapComparison {

        @TestFactory
        @DisplayName("Exact binomial order-statistic bound matches across the bootstrap-comparison baselines")
        Collection<DynamicTest> cases() {
            JsonNode suite = loadSuite("latency_threshold_bootstrap.json");

            var tests = new ArrayList<DynamicTest>();
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                var inputs = c.get("inputs");
                var expected = c.get("expected");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    double[] baselineLatencies = toDoubleArray(inputs.get("baseline_latencies"));
                    double p = inputs.get("p").asDouble();
                    double confidence = inputs.get("confidence").asDouble();

                    LatencyThresholdDeriver.Threshold result =
                            LatencyThresholdDeriver.derive(baselineLatencies, p, confidence);

                    assertThat(result.rank())
                            .as("rank (k) — exact equality")
                            .isEqualTo(expected.get("rank").asInt());
                    assertThat(result.threshold())
                            .as("threshold (t_{(k)}) — exact equality")
                            .isEqualTo(expected.get("threshold").asDouble());
                    assertThat(result.baselinePercentile())
                            .as("baseline percentile (Q(p)) — exact equality")
                            .isEqualTo(expected.get("baseline_percentile").asDouble());
                    assertThat(result.n())
                            .as("n — exact equality")
                            .isEqualTo(expected.get("n").asInt());
                }));
            }
            return tests;
        }

        /**
         * Binomial-conservatism sanity check on the published fixture
         * itself: the binomial threshold is conservative by construction
         * relative to the bootstrap upper bound at the same confidence
         * level, so for every published case the binomial threshold must
         * be greater than or equal to the bootstrap upper bound. If this
         * property ever flips on a future fixture release, the failing
         * case signals either a fixture defect or a methodology drift —
         * fail loudly so neither slips past silently.
         *
         * This is a property check on the oracle's own publication, not
         * a check against {@code LatencyThresholdDeriver}.
         */
        @Test
        @DisplayName("Binomial-conservatism sanity: threshold >= bootstrap_upper holds for every published case")
        void binomialBoundIsAtLeastBootstrapUpper() {
            JsonNode suite = loadSuite("latency_threshold_bootstrap.json");
            for (JsonNode c : suite.get("cases")) {
                String name = c.get("name").asText();
                double threshold = c.get("expected").get("threshold").asDouble();
                double bootstrapUpper = c.get("expected").get("bootstrap_upper").asDouble();
                assertThat(threshold)
                        .as("case=%s: binomial threshold must be >= bootstrap upper "
                                + "(binomial bound is conservative by construction)", name)
                        .isGreaterThanOrEqualTo(bootstrapUpper);
            }
        }
    }
}
