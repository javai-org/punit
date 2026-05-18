package org.javai.punit.api.criterion;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Factory for the §1.4.6 composite verdict-producing strategy.
 * {@link #compose(String, CriterionDecl) compose(...)} names the
 * methodology procedure: given an ordered list of named criterion
 * declarations, the contract verdict is the FAIL-dominant
 * aggregation over their per-criterion verdicts.
 *
 * <p>Statically imported, the call site reads {@code compose(...)}:
 *
 * <pre>{@code
 * import static org.javai.punit.api.criterion.Composite.*;
 *
 * return compose(
 *     "sla",              meeting(0.99, SLA).contractRef("Acme SLA v2, §4.1"),
 *     "valid-structure",  meeting(0.95, SLA).where("parseable", v -> isJson(v)),
 *     "harmful-content",  zeroTolerance(POLICY));
 * }</pre>
 *
 * <p>K=1 with the default criterion id does <em>not</em> use this
 * factory — the bare {@link CriterionDecl} is itself a
 * {@link Criteria} (default id {@value #DEFAULT_CRITERION_ID}).
 * Use {@link #compose(String, CriterionDecl)} with one entry to give
 * a K=1 contract a non-default criterion id.
 */
public final class Composite {

    /** The default criterion id when a bare {@link CriterionDecl} is returned from {@code Contract.criteria()}. */
    public static final String DEFAULT_CRITERION_ID = "contract";

    private Composite() { /* utility class */ }

    /**
     * One named entry in a {@link CompositeCriteria}: a criterion id
     * and the declaration that produces its verdict.
     */
    public record Entry<O>(String id, Decl<O> decl) {
        public Entry {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException(
                        "entry id must be non-blank");
            }
            Objects.requireNonNull(decl, "decl");
        }
    }

    /** Construct an {@link Entry} — the open-ended escape for {@link #composeOf}. */
    public static <O> Entry<O> entry(String id, Decl<O> decl) {
        return new Entry<>(id, decl);
    }

    /** Compose a single named criterion (K=1 with an explicit id). */
    public static <O> CompositeCriteria<O> compose(String id1, Decl<O> d1) {
        return new CompositeCriteria<>(List.of(new Entry<>(id1, d1)));
    }

    /** Compose two named criteria. */
    public static <O> CompositeCriteria<O> compose(
            String id1, Decl<O> d1,
            String id2, Decl<O> d2) {
        return new CompositeCriteria<>(List.of(
                new Entry<>(id1, d1),
                new Entry<>(id2, d2)));
    }

    /** Compose three named criteria. */
    public static <O> CompositeCriteria<O> compose(
            String id1, Decl<O> d1,
            String id2, Decl<O> d2,
            String id3, Decl<O> d3) {
        return new CompositeCriteria<>(List.of(
                new Entry<>(id1, d1),
                new Entry<>(id2, d2),
                new Entry<>(id3, d3)));
    }

    /** Compose four named criteria. */
    public static <O> CompositeCriteria<O> compose(
            String id1, Decl<O> d1,
            String id2, Decl<O> d2,
            String id3, Decl<O> d3,
            String id4, Decl<O> d4) {
        return new CompositeCriteria<>(List.of(
                new Entry<>(id1, d1),
                new Entry<>(id2, d2),
                new Entry<>(id3, d3),
                new Entry<>(id4, d4)));
    }

    /** Compose five named criteria. */
    public static <O> CompositeCriteria<O> compose(
            String id1, Decl<O> d1,
            String id2, Decl<O> d2,
            String id3, Decl<O> d3,
            String id4, Decl<O> d4,
            String id5, Decl<O> d5) {
        return new CompositeCriteria<>(List.of(
                new Entry<>(id1, d1),
                new Entry<>(id2, d2),
                new Entry<>(id3, d3),
                new Entry<>(id4, d4),
                new Entry<>(id5, d5)));
    }

    /** Compose six named criteria. */
    public static <O> CompositeCriteria<O> compose(
            String id1, Decl<O> d1,
            String id2, Decl<O> d2,
            String id3, Decl<O> d3,
            String id4, Decl<O> d4,
            String id5, Decl<O> d5,
            String id6, Decl<O> d6) {
        return new CompositeCriteria<>(List.of(
                new Entry<>(id1, d1),
                new Entry<>(id2, d2),
                new Entry<>(id3, d3),
                new Entry<>(id4, d4),
                new Entry<>(id5, d5),
                new Entry<>(id6, d6)));
    }

    /**
     * Compose an arbitrary number of named criteria — the escape
     * hatch for arities beyond the directly-overloaded {@code compose}
     * forms. Use {@link #entry(String, CriterionDecl)} to construct
     * each pair:
     *
     * <pre>{@code
     * return composeOf(
     *     entry("a", meeting(0.99, SLA)),
     *     entry("b", meeting(0.95, SLA)),
     *     entry("c", meeting(0.90, SLA)),
     *     entry("d", zeroTolerance(POLICY)),
     *     entry("e", empirical()),
     *     entry("f", meeting(0.99, SLA)),
     *     entry("g", meeting(0.99, SLA)));
     * }</pre>
     */
    @SafeVarargs
    public static <O> CompositeCriteria<O> composeOf(Entry<O>... entries) {
        return new CompositeCriteria<>(Arrays.asList(entries));
    }
}
