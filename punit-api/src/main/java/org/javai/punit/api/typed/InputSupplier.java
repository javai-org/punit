package org.javai.punit.api.typed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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
 * <h2>Three construction tiers</h2>
 *
 * <ul>
 *   <li><strong>Tier 1 — {@link #of(List)} / {@link #of(Object[]) of(IT...)}.</strong>
 *       Author supplies inline values; the framework computes a
 *       deterministic content hash as the identity. Fits the bulk
 *       of authoring patterns (primitives, records, strings, enums).
 *       The {@code .inputs(...)} overloads on
 *       {@code Sampling.Builder} are sugar around this tier.</li>
 *   <li><strong>Tier 2 — {@link #named(String, Supplier)}.</strong>
 *       Author labels a supplier; the label <em>is</em> the
 *       identity. Used for curated datasets, large input lists,
 *       fixture data managed outside the codebase. The author's
 *       contract: the label refers to a stable dataset across the
 *       measure's run and any test that references the resulting
 *       baseline.</li>
 *   <li><strong>Tier 3 — {@link #hashed(Supplier, Function)}.</strong>
 *       Author supplies a hash function alongside the supplier.
 *       Used when canonical-encoding doesn't apply (non-stable
 *       {@code toString}, non-serialisable types) but the author
 *       can compute a stable hash from external data (a fixture
 *       file's content hash, a Git tree SHA, a versioned dataset's
 *       checksum).</li>
 * </ul>
 *
 * <h2>Trust model</h2>
 *
 * <p>The framework verifies that two specs reference the
 * <strong>same source</strong> (by identity equality). It does not
 * verify that the source produces the same values across the
 * measure's run and the test's run — that stability is the
 * <em>author's commitment</em>, implicit in each tier:
 *
 * <ul>
 *   <li>Tier 1: the values are part of the source code — if they
 *       change, the hash changes; the framework catches the drift
 *       at evaluate time.</li>
 *   <li>Tier 2: the label is the contract.</li>
 *   <li>Tier 3: the author-supplied hash function is the contract.</li>
 * </ul>
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

    // ── Tier 1 — auto-hashed inline values ──────────────────────────

    /**
     * Tier 1 — auto-hashed inline values. Identity is a SHA-256 of
     * a canonical string encoding of the values
     * ({@code [v0,v1,…]} where each {@code vi} is
     * {@code String.valueOf(values.get(i))}).
     *
     * <p>The encoding depends on each element's {@code toString()}
     * being stable across runs. This holds for primitives, strings,
     * enums, and Java records (record {@code toString} is
     * specified). For custom classes whose {@code toString}
     * includes object identity or is otherwise unstable, prefer
     * Tier 2 or Tier 3.
     *
     * @throws IllegalArgumentException if {@code values} is empty
     */
    static <IT> InputSupplier<IT> of(List<IT> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must be non-empty");
        }
        List<IT> snapshot = List.copyOf(values);
        String id = "sha256:" + sha256(canonicalEncoding(snapshot));
        return new InputSupplier<IT>() {
            @Override public List<IT> all() { return snapshot; }
            @Override public String identity() { return id; }
            @Override public String toString() {
                return "InputSupplier.of(size=" + snapshot.size() + ", " + id + ")";
            }
        };
    }

    /** Varargs form of {@link #of(List)}. */
    @SafeVarargs
    static <IT> InputSupplier<IT> of(IT... values) {
        Objects.requireNonNull(values, "values");
        return of(List.of(values));
    }

    // ── Tier 2 — author-labelled supplier ───────────────────────────

    /**
     * Tier 2 — author-labelled supplier. The label <em>is</em> the
     * identity. The supplier is invoked lazily on first
     * {@link #all()} call and the result memoised.
     *
     * <p>The author asserts: "this label refers to a stable
     * dataset across the measure's run and any test that references
     * the resulting baseline." The framework records the label and
     * trusts that contract. If the author re-uses the label for
     * unrelated data, the framework cannot detect it.
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

    // ── Tier 3 — author-hashed supplier ─────────────────────────────

    /**
     * Tier 3 — author-hashed supplier. The author supplies both the
     * inputs and a hash function over them; the framework records
     * the hash as the identity.
     *
     * <p>Used when canonical encoding doesn't apply but the author
     * can compute a stable hash from external data (a fixture
     * file's content hash, a Git tree SHA, a versioned dataset's
     * checksum). The supplier and hash function are invoked lazily
     * on first {@link #all()} or {@link #identity()} call and the
     * results memoised.
     *
     * <p>The author asserts: "the hash returned by {@code hashFn}
     * is stable across the measure's run and any test that
     * references the resulting baseline."
     */
    static <IT> InputSupplier<IT> hashed(
            Supplier<List<IT>> supplier,
            Function<List<IT>, String> hashFn) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(hashFn, "hashFn");
        return new InputSupplier<IT>() {
            private List<IT> cached;
            private String identity;

            private synchronized void materialise() {
                if (cached != null) {
                    return;
                }
                List<IT> result = Objects.requireNonNull(
                        supplier.get(), "hashed supplier returned null");
                if (result.isEmpty()) {
                    throw new IllegalStateException(
                            "hashed supplier returned an empty list");
                }
                cached = List.copyOf(result);
                identity = Objects.requireNonNull(
                        hashFn.apply(cached), "hashFn returned null");
            }

            @Override public List<IT> all() {
                materialise();
                return cached;
            }

            @Override public String identity() {
                materialise();
                return identity;
            }

            @Override public String toString() {
                return identity == null
                        ? "InputSupplier.hashed(<unmaterialised>)"
                        : "InputSupplier.hashed(" + identity + ")";
            }
        };
    }

    // ── Internal helpers ─────────────────────────────────────────────

    private static String canonicalEncoding(List<?> values) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object v : values) {
            if (!first) {
                sb.append(",");
            }
            sb.append(String.valueOf(v));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK 8+; this branch is unreachable.
            throw new AssertionError("SHA-256 unavailable on this JVM", e);
        }
    }
}
