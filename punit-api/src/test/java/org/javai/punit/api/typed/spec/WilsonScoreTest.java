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

    // ── Wilson-score bounds correctness ─────────────────────────────
    //
    // Cross-checked against the closed-form Wilson interval (Wilson
    // 1927; see SC01 in the orchestrator catalog) at canonical
    // (p, n, confidence) tuples.

    @Test
    @DisplayName("at p=0.9, n=100, c=0.95: lower ≈ 0.8256, upper ≈ 0.9448 — standard Wilson interval")
    void canonicalCase() {
        // Standard Wilson interval (no continuity correction) — the form the
        // SC02 catalog specifies and the Wikipedia §"Wilson score interval"
        // gives. The continuity-corrected variant (Newcombe 1998), used by
        // R's binom::binom.confint, is wider; we use the uncorrected form
        // because it is the one cited in the orchestrator's statistics-core
        // catalog and matches what most published power-analysis derivations
        // assume.
        double[] interval = WilsonScore.interval(0.9, 100, 0.95);

        assertThat(interval[0]).isCloseTo(0.8256, within(0.0001));
        assertThat(interval[1]).isCloseTo(0.9448, within(0.0001));
    }

    @Test
    @DisplayName("at p=0.95, n=20, c=0.95: lower ≈ 0.7639, upper ≈ 0.9911 — small-sample Wilson behaviour")
    void smallSample() {
        double[] interval = WilsonScore.interval(0.95, 20, 0.95);

        assertThat(interval[0]).isCloseTo(0.7639, within(0.001));
        assertThat(interval[1]).isCloseTo(0.9911, within(0.001));
    }

    @Test
    @DisplayName("at p=0.5, n=1000, c=0.95: bounds straddle 0.5 symmetrically")
    void symmetricAt50Percent() {
        double[] interval = WilsonScore.interval(0.5, 1000, 0.95);
        double lowerDistance = 0.5 - interval[0];
        double upperDistance = interval[1] - 0.5;

        assertThat(lowerDistance).isCloseTo(upperDistance, within(1e-3));
    }

    @Test
    @DisplayName("interval narrows as sample size grows")
    void widthShrinksWithN() {
        double width100 = width(WilsonScore.interval(0.9, 100, 0.95));
        double width1000 = width(WilsonScore.interval(0.9, 1000, 0.95));
        double width10000 = width(WilsonScore.interval(0.9, 10000, 0.95));

        assertThat(width100).isGreaterThan(width1000);
        assertThat(width1000).isGreaterThan(width10000);
    }

    @Test
    @DisplayName("interval widens as confidence grows")
    void widthGrowsWithConfidence() {
        double width90 = width(WilsonScore.interval(0.9, 100, 0.90));
        double width95 = width(WilsonScore.interval(0.9, 100, 0.95));
        double width99 = width(WilsonScore.interval(0.9, 100, 0.99));

        assertThat(width99).isGreaterThan(width95);
        assertThat(width95).isGreaterThan(width90);
    }

    @Test
    @DisplayName("at p=0 and p=1, the interval doesn't degenerate to [0,0] / [1,1]")
    void boundaryProportions() {
        // At observed=0, lowerBound is 0 but upperBound is positive — the
        // Wilson interval handles edge cases gracefully (unlike the naive
        // CI which would give [0, 0]).
        double[] atZero = WilsonScore.interval(0.0, 100, 0.95);
        assertThat(atZero[0]).isCloseTo(0.0, within(1e-9));
        assertThat(atZero[1]).isPositive();

        double[] atOne = WilsonScore.interval(1.0, 100, 0.95);
        assertThat(atOne[1]).isCloseTo(1.0, within(1e-9));
        assertThat(atOne[0]).isLessThan(1.0);
    }

    @Test
    @DisplayName("lowerBound and upperBound match interval[0] and interval[1]")
    void singleAccessorsMatchInterval() {
        double[] interval = WilsonScore.interval(0.85, 200, 0.95);

        assertThat(WilsonScore.lowerBound(0.85, 200, 0.95)).isEqualTo(interval[0]);
        assertThat(WilsonScore.upperBound(0.85, 200, 0.95)).isEqualTo(interval[1]);
    }

    // ── Validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("rejects observed outside [0, 1]")
    void rejectsObservedOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(-0.01, 100, 0.95));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(1.01, 100, 0.95));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(Double.NaN, 100, 0.95));
    }

    @Test
    @DisplayName("rejects sampleCount ≤ 0")
    void rejectsNonPositiveSampleCount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(0.5, 0, 0.95));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(0.5, -1, 0.95));
    }

    @Test
    @DisplayName("rejects confidence outside (0, 1)")
    void rejectsConfidenceOutOfRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(0.5, 100, 0.0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(0.5, 100, 1.0));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> WilsonScore.interval(0.5, 100, Double.NaN));
    }

    private static double width(double[] interval) {
        return interval[1] - interval[0];
    }
}
