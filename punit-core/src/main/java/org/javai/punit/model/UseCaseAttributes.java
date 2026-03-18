package org.javai.punit.model;

/**
 * Bundles use-case-level attributes declared on {@code @UseCase}.
 *
 * <p>This value object acts as a single carrier for attributes that flow from
 * the {@code @UseCase} annotation through configs, baselines, specs, and verdicts.
 * Adding a new attribute requires changing only this record plus the semantic
 * sites that act on it — not the ~35 pass-through plumbing files.
 *
 * @param warmup number of warmup invocations to discard before counting (0 = no warmup)
 * @param maxConcurrent maximum concurrent sample executions (0 = framework default)
 */
public record UseCaseAttributes(int warmup, int maxConcurrent) {

    /** Default attributes (no warmup, no concurrency override). */
    public static final UseCaseAttributes DEFAULT = new UseCaseAttributes(0, 0);

    public UseCaseAttributes {
        if (warmup < 0) {
            throw new IllegalArgumentException("warmup must be >= 0, but was " + warmup);
        }
        if (maxConcurrent < 0) {
            throw new IllegalArgumentException("maxConcurrent must be >= 0, but was " + maxConcurrent);
        }
    }

    /**
     * Convenience constructor for warmup-only (maxConcurrent defaults to 0).
     */
    public UseCaseAttributes(int warmup) {
        this(warmup, 0);
    }

    /** Returns true if warmup invocations are configured. */
    public boolean hasWarmup() {
        return warmup > 0;
    }

    /** Returns true if a max concurrency override is configured. */
    public boolean hasMaxConcurrent() {
        return maxConcurrent > 0;
    }
}
