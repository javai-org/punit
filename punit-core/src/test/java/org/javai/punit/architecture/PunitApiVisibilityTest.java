package org.javai.punit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.punit.api.typed.FactorBundle;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: verifies {@code punit-api} types are visible from
 * {@code punit-core}. Catches regressions in the module wiring that
 * would otherwise only be noticed when a downstream consumer compiles.
 */
@DisplayName("punit-api visibility from punit-core")
class PunitApiVisibilityTest {

    record SampleFactors(int n) {}

    @Test
    @DisplayName("types from punit-api are visible and usable")
    void typesAreVisible() {
        UseCaseOutcome<Integer> outcome = UseCaseOutcome.of(42);
        assertThat(outcome.value()).isEqualTo(42);

        FactorBundle bundle = FactorBundle.of(new SampleFactors(3));
        assertThat(bundle.canonicalJson()).isEqualTo("{\"n\":3}");
    }
}
