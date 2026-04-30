package org.javai.punit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.api.typed.Contract;
import org.javai.punit.api.typed.ContractBuilder;
import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.TokenTracker;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: verifies {@code punit-api} types are visible from
 * {@code punit-core}. Catches regressions in the module wiring that
 * would otherwise only be noticed when a downstream consumer compiles.
 */
@DisplayName("punit-api visibility from punit-core")
class PUnitApiVisibilityTest {

    record SampleFactors(int n) {}

    @Test
    @DisplayName("types from punit-api are visible and usable")
    void typesAreVisible() {
        Contract<Object, Integer> contract = new Contract<>() {
            @Override public org.javai.outcome.Outcome<Integer> invoke(Object input, TokenTracker tracker) {
                return org.javai.outcome.Outcome.ok(42);
            }
            @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        };
        UseCaseOutcome<Object, Integer> outcome = new UseCaseOutcome<>(
                org.javai.outcome.Outcome.ok(42), contract,
                java.util.List.of(), java.util.Optional.empty(),
                0L, java.time.Duration.ZERO);
        assertThat(outcome.value().isOk()).isTrue();
        assertThat(outcome.value().getOrThrow()).isEqualTo(42);

        FactorBundle bundle = FactorBundle.of(new SampleFactors(3));
        assertThat(bundle.canonicalJson()).isEqualTo("{\"n\":3}");
    }
}
