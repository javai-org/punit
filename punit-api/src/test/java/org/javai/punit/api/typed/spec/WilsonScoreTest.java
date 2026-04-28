package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WilsonScore — confidence interval for a binomial proportion")
class WilsonScoreTest {

    private static final double TOLERANCE = 1e-6;

    // ── inverse-normal-CDF correctness ──────────────────────────────
    //
    // Cross-checked against published tables (e.g. NIST handbook
    // §1.3.6.7.1) and against scipy.stats.norm.ppf at python 3.

    @Test
    @DisplayName("standardNormalCriticalValue at 95% ≈ 1.959964")
    void zStarAt95() {
        assertThat(WilsonScore.standardNormalCriticalValue(0.95))
                .isCloseTo(1.959964, within(TOLERANCE));
    }

    @Test
    @DisplayName("standardNormalCriticalValue at 99% ≈ 2.575829")
    void zStarAt99() {
        assertThat(WilsonScore.standardNormalCriticalValue(0.99))
                .isCloseTo(2.575829, within(TOLERANCE));
    }

    @Test
    @DisplayName("standardNormalCriticalValue at 90% ≈ 1.644854")
    void zStarAt90() {
        assertThat(WilsonScore.standardNormalCriticalValue(0.90))
                .isCloseTo(1.644854, within(TOLERANCE));
    }

    @Test
    @DisplayName("inverseStandardNormalCdf is monotonically increasing")
    void monotonic() {
        double prev = WilsonScore.inverseStandardNormalCdf(0.001);
        for (double p = 0.01; p < 1.0; p += 0.01) {
            double next = WilsonScore.inverseStandardNormalCdf(p);
            assertThat(next).isGreaterThan(prev);
            prev = next;
        }
    }

    @Test
    @DisplayName("inverseStandardNormalCdf at 0.5 is 0")
    void invCdfAtMedian() {
        assertThat(WilsonScore.inverseStandardNormalCdf(0.5))
                .isCloseTo(0.0, within(1e-9));
    }

    // ── Wilson-score lower bound correctness ────────────────────────
    //
    // Cross-checked against the closed-form Wilson interval (Wilson
    // 1927; see SC01 in the orchestrator catalog) at canonical
    // (p, n, confidence) tuples.

    @Test
    @DisplayName("at p=0.9, n=100, c=0.95: lower ≈ 0.8256 — standard Wilson interval")
    void canonicalCase() {
        // Standard Wilson interval (no continuity correction) — the form the
        // SC02 catalog specifies and the Wikipedia §"Wilson score interval"
        // gives. The continuity-corrected variant (Newcombe 1998), used by
        // R's binom::binom.confint, is wider; we use the uncorrected form
        // because it is the one cited in the orchestrator's statistics-core
        // catalog and matches what most published power-analysis derivations
        // assume.
        assertThat(WilsonScore.lowerBound(0.9, 100, 0.95))
                .isCloseTo(0.8256, within(0.0001));
    }

    @Test
    @DisplayName("at p=0.95, n=20, c=0.95: lower ≈ 0.7639 — small-sample Wilson behaviour")
    void smallSample() {
        assertThat(WilsonScore.lowerBound(0.95, 20, 0.95))
                .isCloseTo(0.7639, within(0.001));
    }

    @Test
    @DisplayName("lower bound rises as sample size grows for fixed p > 0.5")
    void lowerBoundShrinksWithN() {
        double lower100 = WilsonScore.lowerBound(0.9, 100, 0.95);
        double lower1000 = WilsonScore.lowerBound(0.9, 1000, 0.95);
        double lower10000 = WilsonScore.lowerBound(0.9, 10000, 0.95);

        assertThat(lower1000).isGreaterThan(lower100);
        assertThat(lower10000).isGreaterThan(lower1000);
    }

    @Test
    @DisplayName("lower bound shrinks as confidence grows — higher confidence is more conservative")
    void lowerBoundShrinksWithConfidence() {
        double lower90 = WilsonScore.lowerBound(0.9, 100, 0.90);
        double lower95 = WilsonScore.lowerBound(0.9, 100, 0.95);
        double lower99 = WilsonScore.lowerBound(0.9, 100, 0.99);

        assertThat(lower95).isLessThan(lower90);
        assertThat(lower99).isLessThan(lower95);
    }

    @Test
    @DisplayName("at p=0, the lower bound is 0 (no degenerate negative values)")
    void boundaryAtZero() {
        assertThat(WilsonScore.lowerBound(0.0, 100, 0.95))
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("at p=1, the lower bound is below 1 — Wilson handles edge cases gracefully")
    void boundaryAtOne() {
        assertThat(WilsonScore.lowerBound(1.0, 100, 0.95)).isLessThan(1.0);
    }

    // ── Validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("rejects observed outside [0, 1]")
    void rejectsObservedOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(-0.01, 100, 0.95));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(1.01, 100, 0.95));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(Double.NaN, 100, 0.95));
    }

    @Test
    @DisplayName("rejects sampleCount ≤ 0")
    void rejectsNonPositiveSampleCount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(0.5, 0, 0.95));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(0.5, -1, 0.95));
    }

    @Test
    @DisplayName("rejects confidence outside (0, 1)")
    void rejectsConfidenceOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(0.5, 100, 0.0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(0.5, 100, 1.0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.lowerBound(0.5, 100, Double.NaN));
    }
}
