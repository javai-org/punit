package org.javai.punit.api.criterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The §1.4.6 composite verdict-producing strategy: a list of named
 * {@link CriterionDecl}s, evaluated independently and aggregated
 * FAIL-dominantly to resolve the contract verdict.
 *
 * <p>Constructed via {@link Composite#compose(String, CriterionDecl)
 * Composite.compose(...)} (or {@link Composite#composeOf(Composite.Entry[])
 * Composite.composeOf(entries...)} for arities beyond the overload
 * cap). Authors do not construct this type directly.
 *
 * <p>Composition order is preserved — the verdict path renders
 * per-criterion rows in declaration order.
 *
 * @param <O> the contract's per-sample output value type
 */
public final class CompositeCriteria<O> implements Criteria<O> {

    private final List<Composite.Entry<O>> entries;

    CompositeCriteria(List<Composite.Entry<O>> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "CompositeCriteria requires at least one entry");
        }
        for (Composite.Entry<O> e : entries) {
            Objects.requireNonNull(e, "entry");
        }
        rejectDuplicateIds(entries);
        this.entries = List.copyOf(entries);
    }

    /** Entries in declaration order. */
    public List<Composite.Entry<O>> entries() {
        return entries;
    }

    @Override
    public List<Criterion<O>> asList() {
        List<Criterion<O>> out = new ArrayList<>(entries.size());
        for (Composite.Entry<O> e : entries) {
            out.add(e.decl().toRuntime(e.id()));
        }
        return List.copyOf(out);
    }

    private static <O> void rejectDuplicateIds(List<Composite.Entry<O>> entries) {
        java.util.Set<String> seen = new java.util.HashSet<>(entries.size() * 2);
        for (Composite.Entry<O> e : entries) {
            if (!seen.add(e.id())) {
                throw new IllegalArgumentException(
                        "duplicate criterion id '" + e.id() + "' in compose(...)");
            }
        }
    }
}
