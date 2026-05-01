package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UseCase defaults")
class UseCaseTest {

    private static class SimpleUseCase implements UseCase<Object, String, Integer> {
        @Override public void postconditions(ContractBuilder<Integer> b) { /* none */ }
        @Override public Outcome<Integer> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input.length());
        }
    }

    private static class ShoppingBasketUseCase implements UseCase<Object, String, String> {
        @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
    }

    private static class HTTPClientUseCase implements UseCase<Object, String, String> {
        @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
    }

    private static class PaymentGateway implements UseCase<Object, String, String> {
        @Override public void postconditions(ContractBuilder<String> b) { /* none */ }
        @Override public Outcome<String> invoke(String input, TokenTracker tracker) {
            return Outcome.ok(input);
        }
    }

    @Test
    @DisplayName("default id strips a UseCase suffix and kebab-cases the remainder")
    void defaultIdStripsSuffixAndKebabCases() {
        assertThat(new ShoppingBasketUseCase().id()).isEqualTo("shopping-basket");
    }

    @Test
    @DisplayName("default id kebab-cases a name with no UseCase suffix")
    void defaultIdKebabCasesBareName() {
        assertThat(new PaymentGateway().id()).isEqualTo("payment-gateway");
    }

    @Test
    @DisplayName("default id treats consecutive capitals followed by lowercase as a boundary")
    void defaultIdHandlesAcronymBoundaries() {
        assertThat(new HTTPClientUseCase().id()).isEqualTo("http-client");
    }

    @Test
    @DisplayName("default id falls back to simple name when there is no UseCase suffix to strip")
    void defaultIdForSimpleName() {
        assertThat(new SimpleUseCase().id()).isEqualTo("simple");
    }

    @Test
    @DisplayName("description defaults to the empty string")
    void descriptionDefault() {
        assertThat(new SimpleUseCase().description()).isEmpty();
    }

    @Test
    @DisplayName("warmup defaults to zero")
    void warmupDefault() {
        assertThat(new SimpleUseCase().warmup()).isZero();
    }

    @Test
    @DisplayName("pacing defaults to unlimited")
    void pacingDefaultsUnlimited() {
        assertThat(new SimpleUseCase().pacing()).isSameAs(Pacing.unlimited());
    }

    @Test
    @DisplayName("covariates defaults to an empty list")
    void covariatesDefaultsEmpty() {
        assertThat(new SimpleUseCase().covariates()).isEmpty();
    }

    @Test
    @DisplayName("apply produces an Ok outcome wrapping the invoke result")
    void applyWrapsOutputValue() {
        var outcome = new SimpleUseCase().apply("hello", TokenTracker.create());
        assertThat(outcome.value().isOk()).isTrue();
        assertThat(outcome.value().getOrThrow()).isEqualTo(5);
    }
}
