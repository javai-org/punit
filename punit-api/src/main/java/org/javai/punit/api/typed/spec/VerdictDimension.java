package org.javai.punit.api.typed.spec;

/**
 * Which measurement dimension(s) drive a probabilistic test's overall
 * verdict.
 *
 * <p>Every run produces both a functional (pass-rate) verdict and a
 * latency verdict; {@code VerdictDimension} selects which projection
 * the spec exposes through its single-valued {@code verdict()}
 * accessor.
 *
 * <p>Defaults:
 * <ul>
 *   <li>{@link #BOTH} — when the spec declares both a functional
 *       threshold and a {@link LatencySpec}.</li>
 *   <li>{@link #FUNCTIONAL} — when no latency assertion is
 *       configured.</li>
 * </ul>
 */
public enum VerdictDimension {

    /** Only the functional (pass-rate) verdict drives the outcome. */
    FUNCTIONAL,

    /** Only the latency verdict drives the outcome. */
    LATENCY,

    /** Both must pass for the combined outcome to be PASS. */
    BOTH
}
