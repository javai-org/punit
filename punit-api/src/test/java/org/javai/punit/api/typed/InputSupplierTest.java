package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InputSupplier.named — author-labelled supplier")
class InputSupplierTest {

    @Test
    @DisplayName("identity equals the label")
    void identityIsLabel() {
        InputSupplier<String> s = InputSupplier.named(
                "shopping-instructions-v3", () -> List.of("a", "b"));

        assertThat(s.identity()).isEqualTo("shopping-instructions-v3");
    }

    @Test
    @DisplayName("materialises supplier lazily and memoises")
    void lazyAndMemoised() {
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
    @DisplayName("rejects blank label")
    void rejectsBlankLabel() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> InputSupplier.<String>named("", () -> List.of("a")));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> InputSupplier.<String>named("   ", () -> List.of("a")));
    }

    @Test
    @DisplayName("rejects null label and null supplier")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>named(null, () -> List.of("a")));
        assertThatNullPointerException()
                .isThrownBy(() -> InputSupplier.<String>named("x", null));
    }

    @Test
    @DisplayName("supplier returning empty list throws on first all() call")
    void emptySupplierThrows() {
        InputSupplier<String> s = InputSupplier.named("x", List::of);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(s::all)
                .withMessageContaining("empty");
    }
}
