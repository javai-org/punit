package org.javai.punit.api.typed;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Named source of inputs, used as the unit of <em>sampling-frame
 * identity</em> for empirical pairing.
 *
 * <p>Authoring the inputs of a {@link Sampling} as a labelled
 * source (rather than as a transient {@code List<IT>}) lets the
 * framework verify that a probabilistic test and its baseline
 * measure draw from the same population — the structural premise
 * every empirical comparison rests on. See
 * {@code docs/DES-INPUTS-IDENTITY-SUPPLIER.md} for the full design
 * intent and {@code DG01-sampling/README.md} for the integrity
 * mechanism on top of which this type sits.
 *
 * <h2>Two authoring shapes</h2>
 *
 * <p><strong>Inline values.</strong> Authors writing
 * {@code .inputs("a", "b", "c")} or {@code .inputs(values)} on the
 * {@link Sampling.Builder} get an internally-synthesised supplier
 * whose identity is a deterministic content hash of the values.
 * Stable for primitives, strings, enums, and Java records (whose
 * {@code toString} is specified). No author-side ceremony — the
 * builder handles it.
 *
 * <p><strong>Named source.</strong> Authors with curated datasets,
 * large input lists, fixture data managed outside the codebase, or
 * any source where canonical encoding doesn't apply use
 * {@link #named(String, Supplier)}. The label <em>is</em> the
 * identity; the framework records it and trusts the author's
 * commitment that the label refers to a stable dataset across the
 * measure's run and any test that references the resulting baseline.
 *
 * <h2>Trust model</h2>
 *
 * <p>The framework verifies that two specs reference the
 * <strong>same source</strong> (by identity equality). It does not
 * verify that the source produces the same values across runs —
 * that stability is the <em>author's commitment</em> implicit in
 * each authoring shape (the values are in source code for inline,
 * or the label asserts stability for named).
 *
 * <p>Threat model: <em>accidental</em> drift. Adversarial reuse of
 * a label across unrelated datasets is out of scope; the framework
 * cannot tell the author has broken their own contract.
 *
 * @param <IT> the per-sample input type
 */
public interface InputSupplier<IT> {

    /**
     * @return the inputs themselves, in canonical order. Non-empty.
     *         Implementations may materialise lazily on first call.
     */
    List<IT> all();

    /**
     * @return a stable string identifying this source. Two
     *         suppliers with the same identity assert sampling-
     *         frame equivalence.
     */
    String identity();

    /**
     * Author-labelled supplier. The label <em>is</em> the identity.
     * The supplier is invoked lazily on first {@link #all()} call
     * and the result memoised.
     *
     * <p>The author asserts: "this label refers to a stable
     * dataset across the measure's run and any test that
     * references the resulting baseline." The framework records
     * the label and trusts that contract. If the author re-uses
     * the label for unrelated data, the framework cannot detect
     * it.
     *
     * @param label    a stable, non-blank identifier for the source
     * @param supplier produces the input list lazily
     * @throws IllegalArgumentException if {@code label} is blank
     */
    static <IT> InputSupplier<IT> named(String label, Supplier<List<IT>> supplier) {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must be non-blank");
        }
        Objects.requireNonNull(supplier, "supplier");
        return new InputSupplier<IT>() {
            private List<IT> cached;

            @Override public synchronized List<IT> all() {
                if (cached == null) {
                    List<IT> result = Objects.requireNonNull(
                            supplier.get(), "named supplier '" + label + "' returned null");
                    if (result.isEmpty()) {
                        throw new IllegalStateException(
                                "named supplier '" + label + "' returned an empty list");
                    }
                    cached = List.copyOf(result);
                }
                return cached;
            }

            @Override public String identity() { return label; }

            @Override public String toString() {
                return "InputSupplier.named(" + label + ")";
            }
        };
    }
}
