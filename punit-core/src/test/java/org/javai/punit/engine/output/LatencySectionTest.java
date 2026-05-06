package org.javai.punit.engine.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.javai.punit.api.LatencyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the LT01 {@code latency:} block builder.
 *
 * <p>Three cases:
 * <ul>
 *   <li>Zero passing samples → empty Optional (no block to emit).</li>
 *   <li>Some passing samples → block carries the indicator triple
 *       ({@code basis} / {@code contributingSamples} / {@code totalSamples})
 *       plus the four percentiles in milliseconds.</li>
 *   <li>All samples passing → contributingSamples == totalSamples.</li>
 * </ul>
 */
@DisplayName("LatencySection — LT01 block shape, indicator triple, zero-passing edge case")
class LatencySectionTest {

    @Test
    @DisplayName("Returns empty Optional when no samples passed")
    void emptyWhenNoPassingSamples() {
        assertThat(LatencySection.blockFor(LatencyResult.empty(), 0, 20)).isEmpty();
    }

    @Test
    @DisplayName("Block carries the LT01 indicator triple plus four percentiles in milliseconds")
    void blockShapeWhenPassingSamplesPresent() {
        LatencyResult passing = new LatencyResult(
                Duration.ofMillis(42),
                Duration.ofMillis(78),
                Duration.ofMillis(95),
                Duration.ofMillis(150),
                18);

        Optional<Map<String, Object>> block = LatencySection.blockFor(passing, 18, 20);
        assertThat(block).isPresent();
        Map<String, Object> map = block.get();
        assertThat(map).containsExactly(
                Map.entry("basis", "passing-samples"),
                Map.entry("contributingSamples", 18),
                Map.entry("totalSamples", 20),
                Map.entry("p50Ms", 42L),
                Map.entry("p90Ms", 78L),
                Map.entry("p95Ms", 95L),
                Map.entry("p99Ms", 150L));
    }

    @Test
    @DisplayName("contributingSamples == totalSamples when every sample passed")
    void allSamplesPassing() {
        LatencyResult passing = new LatencyResult(
                Duration.ofMillis(10),
                Duration.ofMillis(20),
                Duration.ofMillis(25),
                Duration.ofMillis(30),
                10);

        Map<String, Object> block = LatencySection.blockFor(passing, 10, 10).orElseThrow();
        assertThat(block).containsEntry("contributingSamples", 10);
        assertThat(block).containsEntry("totalSamples", 10);
    }
}
