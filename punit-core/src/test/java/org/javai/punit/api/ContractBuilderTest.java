package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContractBuilder — accumulates clauses fluently")
class ContractBuilderTest {

    @Nested
    @DisplayName("ensure")
    class Ensures {

        @Test
        @DisplayName("a single ensure call yields one Leaf clause")
        void singleEnsureYieldsOneLeaf() {
            ContractBuilder<String> b = new ContractBuilder<>();

            b.ensure("non-empty", s -> s.isEmpty()
                    ? Outcome.fail("empty", "was empty")
                    : Outcome.ok());

            List<Postcondition<String>> clauses = b.build();

            assertThat(clauses).hasSize(1);
            assertThat(clauses.get(0)).isInstanceOf(Postcondition.Leaf.class);
            assertThat(clauses.get(0).description()).isEqualTo("non-empty");
        }

        @Test
        @DisplayName("multiple ensures chain in order")
        void multipleEnsuresChain() {
            List<Postcondition<Integer>> clauses = new ContractBuilder<Integer>()
                    .ensure("positive", v -> v > 0 ? Outcome.ok() : Outcome.fail("non-positive", ""))
                    .ensure("even",     v -> v % 2 == 0 ? Outcome.ok() : Outcome.fail("odd", ""))
                    .ensure("small",    v -> v < 100 ? Outcome.ok() : Outcome.fail("too-big", ""))
                    .build();

            assertThat(clauses).extracting(Postcondition::description)
                    .containsExactly("positive", "even", "small");
        }

        @Test
        @DisplayName("blank description rejected at clause construction")
        void blankDescriptionRejected() {
            ContractBuilder<Object> b = new ContractBuilder<>();

            assertThatThrownBy(() -> b.ensure("", v -> Outcome.ok()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the empty builder builds an empty clause list")
        void emptyBuilder() {
            assertThat(new ContractBuilder<String>().build()).isEmpty();
        }
    }

    @Nested
    @DisplayName("build")
    class Build {

        @Test
        @DisplayName("the returned list is immutable")
        void buildReturnsImmutable() {
            List<Postcondition<String>> clauses = new ContractBuilder<String>()
                    .ensure("a", s -> Outcome.ok())
                    .build();

            assertThatThrownBy(() ->
                    clauses.add(new Postcondition.Leaf<>("x", v -> Outcome.ok())))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
