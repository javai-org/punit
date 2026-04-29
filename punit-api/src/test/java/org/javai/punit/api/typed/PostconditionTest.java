package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.punit.api.typed.Postcondition.deriving;
import static org.javai.punit.api.typed.Postcondition.ensure;

import java.util.List;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Postcondition — unopinionated evaluation of one aspect")
class PostconditionTest {

    @Nested
    @DisplayName("ensure")
    class Ensure {

        @Test
        @DisplayName("Outcome.ok → passed; description carried through")
        void passes() {
            Postcondition<String> p = ensure("non-empty",
                    s -> s.isEmpty()
                            ? Outcome.fail("empty", "string was empty")
                            : Outcome.ok());

            PostconditionResult r = p.evaluate("hello");

            assertThat(r.passed()).isTrue();
            assertThat(r.description()).isEqualTo("non-empty");
        }

        @Test
        @DisplayName("Outcome.fail's reason is preserved")
        void preservesFailureReason() {
            Postcondition<Integer> p = ensure("positive",
                    v -> v > 0
                            ? Outcome.ok()
                            : Outcome.fail("negative", "got " + v));

            PostconditionResult r = p.evaluate(-3);

            assertThat(r.failed()).isTrue();
            assertThat(r.failureReason()).contains("got -3");
        }

        @Test
        @DisplayName("a thrown RuntimeException is captured as a failure")
        void thrownExceptionCaptured() {
            Postcondition<Integer> p = ensure("checked", v -> {
                throw new IllegalStateException("boom");
            });

            PostconditionResult r = p.evaluate(0);

            assertThat(r.failed()).isTrue();
            assertThat(r.failureReason()).contains("boom");
        }

        @Test
        @DisplayName("evaluateAll on a leaf returns a singleton list")
        void evaluateAllLeaf() {
            Postcondition<String> p = ensure("non-empty",
                    s -> s.isEmpty()
                            ? Outcome.fail("empty", "")
                            : Outcome.ok());

            List<PostconditionResult> results = p.evaluateAll("hello");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("deriving(...)")
    class Derivations {

        @Test
        @DisplayName("derivation succeeds → derivation pass + nested results")
        void derivationSucceeds() {
            Postcondition<String> p = deriving("parsed length",
                    s -> Outcome.ok(s.length()),
                    ensure("at least 3", n -> n >= 3
                            ? Outcome.ok()
                            : Outcome.fail("too-short", "n=" + n)),
                    ensure("at most 10", n -> n <= 10
                            ? Outcome.ok()
                            : Outcome.fail("too-long", "n=" + n)));

            List<PostconditionResult> results = p.evaluateAll("hello");

            assertThat(results).hasSize(3);
            assertThat(results.get(0).description()).isEqualTo("parsed length");
            assertThat(results.get(0).passed()).isTrue();
            assertThat(results.get(1).description()).isEqualTo("at least 3");
            assertThat(results.get(1).passed()).isTrue();
            assertThat(results.get(2).description()).isEqualTo("at most 10");
            assertThat(results.get(2).passed()).isTrue();
        }

        @Test
        @DisplayName("derivation Outcome.fail → nested ensures reported as skipped")
        void derivationFailsSkipsNested() {
            Postcondition<String> p = deriving("parsed",
                    s -> Outcome.<Integer>fail("parse-error", "malformed"),
                    ensure("inner-1", (Integer n) -> Outcome.ok()),
                    ensure("inner-2", (Integer n) -> Outcome.ok()));

            List<PostconditionResult> results = p.evaluateAll("garbage");

            assertThat(results).hasSize(3);
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).failureReason()).contains("malformed");
            assertThat(results.get(1).failed()).isTrue();
            assertThat(results.get(1).failureReason())
                    .hasValueSatisfying(r -> assertThat(r).contains("skipped"));
            assertThat(results.get(2).failed()).isTrue();
            assertThat(results.get(2).failureReason())
                    .hasValueSatisfying(r -> assertThat(r).contains("skipped"));
        }

        @Test
        @DisplayName("derivation throws → derivation fails, nested skipped")
        void derivationThrowsSkipsNested() {
            Postcondition<String> p = deriving("parsed",
                    (String s) -> { throw new RuntimeException("kaboom"); },
                    ensure("inner", (Integer n) -> Outcome.ok()));

            List<PostconditionResult> results = p.evaluateAll("anything");

            assertThat(results).hasSize(2);
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(0).failureReason()).contains("kaboom");
            assertThat(results.get(1).failureReason())
                    .hasValueSatisfying(r -> assertThat(r).contains("skipped"));
        }

        @Test
        @DisplayName("evaluate (singular) collapses to derivation's own outcome")
        void evaluateSingleCollapsesToDerivation() {
            Postcondition<String> p = deriving("parsed",
                    s -> Outcome.ok(s.length()),
                    ensure("inner", (Integer n) -> Outcome.fail("nope", "x")));

            PostconditionResult r = p.evaluate("hello");

            assertThat(r.passed()).isTrue();
            assertThat(r.description()).isEqualTo("parsed");
        }

        @Test
        @DisplayName("nested derivations propagate skips through every level")
        void nestedDerivationsSkipChain() {
            Postcondition<Integer> innerDerived = deriving("doubled",
                    n -> Outcome.ok(n * 2),
                    ensure("doubled positive", (Integer d) -> d > 0
                            ? Outcome.ok()
                            : Outcome.fail("non-positive", "d=" + d)));

            Postcondition<String> p = deriving("length",
                    s -> Outcome.<Integer>fail("err", "bad"),
                    innerDerived);

            List<PostconditionResult> results = p.evaluateAll("anything");

            assertThat(results).hasSize(3);
            assertThat(results.get(0).description()).isEqualTo("length");
            assertThat(results.get(0).failed()).isTrue();
            assertThat(results.get(1).description()).isEqualTo("doubled");
            assertThat(results.get(1).failed()).isTrue();
            assertThat(results.get(2).description()).isEqualTo("doubled positive");
            assertThat(results.get(2).failed()).isTrue();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("blank description rejected")
        void blankDescriptionRejected() {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> ensure("", (Object v) -> Outcome.ok()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
