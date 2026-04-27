package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InputSupplier")
class InputSupplierTest {

    record Item(String name, int value) {}

    // ── Tier 1 — auto-hashed inline values ──────────────────────────

    @Test
    @DisplayName("of(...) is deterministic for the same content + same order")
    void tier1Deterministic() {
        InputSupplier<String> a = InputSupplier.of("a", "b", "c");
        InputSupplier<String> b = InputSupplier.of("a", "b", "c");

        assertThat(a.identity()).isEqualTo(b.identity());
        assertThat(a.identity()).startsWith("sha256:");
    }

    @Test
    @DisplayName("of(...) identity differs when ordering differs")
    void tier1OrderingMatters() {
        InputSupplier<String> a = InputSupplier.of("a", "b", "c");
        InputSupplier<String> b = InputSupplier.of("c", "b", "a");

        assertThat(a.identity()).isNotEqualTo(b.identity());
    }

    @Test
    @DisplayName("of(...) identity differs when content differs")
    void tier1ContentMatters() {
        InputSupplier<String> a = InputSupplier.of("a", "b", "c");
        InputSupplier<String> b = InputSupplier.of("a", "b", "d");

        assertThat(a.identity()).isNotEqualTo(b.identity());
    }

    @Test
    @DisplayName("of(...) all() returns the supplied values in order")
    void tier1AllReturnsValues() {
        InputSupplier<String> s = InputSupplier.of("a", "b", "c");
        assertThat(s.all()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("of(...) handles records by their auto-generated toString")
    void tier1HandlesRecords() {
        InputSupplier<Item> a = InputSupplier.of(new Item("x", 1), new Item("y", 2));
        InputSupplier<Item> b = InputSupplier.of(new Item("x", 1), new Item("y", 2));

        assertThat(a.identity()).isEqualTo(b.identity());
    }

    @Test
    @DisplayName("of(...) varargs and List forms produce the same identity")
    void tier1VarargsAndListEquivalent() {
        InputSupplier<String> v = InputSupplier.of("a", "b");
        InputSupplier<String> l = InputSupplier.of(List.of("a", "b"));

        assertThat(v.identity()).isEqualTo(l.identity());
    }

    @Test
    @DisplayName("of(...) rejects empty inputs")
    void tier1RejectsEmpty() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> InputSupplier.<String>of(List.of()))
                .withMessageContaining("non-empty");
    }

    @Test
    @DisplayName("of(...) rejects null inputs")
    void tier1RejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>of((List<String>) null));
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>of((String[]) null));
    }

    // ── Tier 2 — author-labelled supplier ──────────────────────────

    @Test
    @DisplayName("named(...) identity equals the label")
    void tier2IdentityIsLabel() {
        InputSupplier<String> s = InputSupplier.named(
                "shopping-instructions-v3", () -> List.of("a", "b"));

        assertThat(s.identity()).isEqualTo("shopping-instructions-v3");
    }

    @Test
    @DisplayName("named(...) materialises supplier lazily and memoises")
    void tier2LazyAndMemoised() {
        AtomicInteger calls = new AtomicInteger();
        InputSupplier<String> s = InputSupplier.named("x", () -> {
            calls.incrementAndGet();
            return List.of("a", "b");
        });

        assertThat(calls.get()).isZero();           // not yet materialised
        assertThat(s.identity()).isEqualTo("x");    // identity does NOT trigger materialisation
        assertThat(calls.get()).isZero();
        assertThat(s.all()).containsExactly("a", "b");
        assertThat(s.all()).containsExactly("a", "b");
        assertThat(calls.get()).isEqualTo(1);       // memoised after first call
    }

    @Test
    @DisplayName("named(...) rejects blank label")
    void tier2RejectsBlankLabel() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> InputSupplier.<String>named("", () -> List.of("a")));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> InputSupplier.<String>named("   ", () -> List.of("a")));
    }

    @Test
    @DisplayName("named(...) rejects null label and null supplier")
    void tier2RejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>named(null, () -> List.of("a")));
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>named("x", null));
    }

    @Test
    @DisplayName("named(...) supplier returning empty list throws on first all() call")
    void tier2EmptySupplierThrows() {
        InputSupplier<String> s = InputSupplier.named("x", List::of);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(s::all)
                .withMessageContaining("empty");
    }

    // ── Tier 3 — author-hashed supplier ─────────────────────────────

    @Test
    @DisplayName("hashed(...) identity is the author's hash function output")
    void tier3IdentityIsAuthorComputed() {
        InputSupplier<String> s = InputSupplier.hashed(
                () -> List.of("a", "b"),
                inputs -> "fixture@v3:" + inputs.size());

        assertThat(s.identity()).isEqualTo("fixture@v3:2");
        assertThat(s.all()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("hashed(...) materialises lazily and memoises")
    void tier3LazyAndMemoised() {
        AtomicInteger supplierCalls = new AtomicInteger();
        AtomicInteger hashCalls = new AtomicInteger();
        InputSupplier<String> s = InputSupplier.hashed(
                () -> { supplierCalls.incrementAndGet(); return List.of("a"); },
                inputs -> { hashCalls.incrementAndGet(); return "h"; });

        assertThat(supplierCalls.get()).isZero();
        assertThat(s.identity()).isEqualTo("h");
        assertThat(supplierCalls.get()).isEqualTo(1);
        assertThat(hashCalls.get()).isEqualTo(1);
        s.all();
        s.identity();
        assertThat(supplierCalls.get()).isEqualTo(1);   // memoised
        assertThat(hashCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("hashed(...) rejects null arguments")
    void tier3RejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>hashed(null, inputs -> "h"));
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>hashed(() -> List.of("a"), null));
    }
}
