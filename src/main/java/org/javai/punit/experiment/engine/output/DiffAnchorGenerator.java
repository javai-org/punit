package org.javai.punit.experiment.engine.output;

import java.util.Random;

/**
 * Generates deterministic anchor values for diff-aligned sample blocks.
 *
 * <p>Anchors are 8-character lowercase hexadecimal strings derived from a
 * fixed seed and a sample index. The same seed and index always produce
 * the same anchor, guaranteeing that two exploration files from the same
 * experiment run contain identical anchors at identical positions.
 *
 * <p>The seed is hard-coded (not time-based), so anchors are also stable
 * across reruns of the same experiment with the same sample count.
 */
public final class DiffAnchorGenerator {

    private static final long SEED = 42L;

    private DiffAnchorGenerator() {}

    /**
     * Returns the anchor string for the given sample index.
     *
     * <p>Uses {@link java.util.Random} seeded at {@code 42L}, advanced to
     * position {@code sampleIndex} by generating and discarding intermediate
     * values. The anchor is the lower 32 bits of the {@code nextLong()} call,
     * formatted as 8-character zero-padded lowercase hex.
     *
     * @param sampleIndex the zero-based sample index
     * @return 8-character lowercase hex string (e.g. "a3b1799d")
     * @throws IllegalArgumentException if sampleIndex is negative
     */
    public static String anchorFor(int sampleIndex) {
        if (sampleIndex < 0) {
            throw new IllegalArgumentException("sampleIndex must be non-negative");
        }
        Random rng = new Random(SEED);
        for (int i = 0; i < sampleIndex; i++) {
            rng.nextLong();
        }
        long value = rng.nextLong();
        return String.format("%08x", (int) value);
    }

    /**
     * Returns the full anchor comment line for a sample.
     *
     * <p>Format: {@code # ──── sample[N] ──── anchor:XXXXXXXX ────}
     *
     * @param sampleIndex the zero-based sample index
     * @return the complete anchor comment line (no trailing newline)
     */
    public static String anchorLine(int sampleIndex) {
        return "# \u2500\u2500\u2500\u2500 sample[" + sampleIndex + "] \u2500\u2500\u2500\u2500 anchor:" + anchorFor(sampleIndex) + " \u2500\u2500\u2500\u2500";
    }
}
