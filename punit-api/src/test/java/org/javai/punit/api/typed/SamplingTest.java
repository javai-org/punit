package org.javai.punit.api.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.javai.punit.api.typed.spec.BudgetExhaustionPolicy;
import org.javai.punit.api.typed.spec.ExceptionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sampling")
class SamplingTest {

    record Factors(String model, double temperature) {}

    static final class EchoUseCase implements UseCase<Factors, String, String> {
        EchoUseCase(Factors factors) {}

        @Override
        public UseCaseOutcome<String> apply(String input) {
            return UseCaseOutcome.ok(input);
        }

        @Override
        public String id() {
            return "echo";
        }
    }

    private Sampling.Builder<Factors, String, String> baseBuilder() {
        return Sampling.<Factors, String, String>builder()
                .useCaseFactory(EchoUseCase::new)
                .inputs("alpha", "beta");
    }

    @Test
    @DisplayName("builder produces a sampling with the declared fields")
    void buildsWithDeclaredFields() {
        Sampling<Factors, String, String> sampling = baseBuilder()
                .samples(250)
                .timeBudget(Duration.ofSeconds(30))
                .tokenBudget(10_000)
                .tokenCharge(5)
                .onBudgetExhausted(BudgetExhaustionPolicy.PASS_INCOMPLETE)
                .onException(ExceptionPolicy.FAIL_SAMPLE)
                .maxExampleFailures(3)
                .build();

        assertThat(sampling.inputs()).containsExactly("alpha", "beta");
        assertThat(sampling.samples()).isEqualTo(250);
        assertThat(sampling.timeBudget()).contains(Duration.ofSeconds(30));
        assertThat(sampling.tokenBudget().getAsLong()).isEqualTo(10_000L);
        assertThat(sampling.tokenCharge()).isEqualTo(5L);
        assertThat(sampling.budgetPolicy()).isEqualTo(BudgetExhaustionPolicy.PASS_INCOMPLETE);
        assertThat(sampling.exceptionPolicy()).isEqualTo(ExceptionPolicy.FAIL_SAMPLE);
        assertThat(sampling.maxExampleFailures()).isEqualTo(3);
    }

    @Test
    @DisplayName("default samples is 1000")
    void defaultSamples() {
        Sampling<Factors, String, String> sampling = baseBuilder().build();
        assertThat(sampling.samples()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Sampling carries no factors — neither builder nor accessor exposes them")
    void factorFreeInvariant() {
        for (var method : Sampling.class.getMethods()) {
            assertThat(method.getName())
                    .as("Sampling method %s", method)
                    .isNotEqualTo("factors");
        }
        for (var method : Sampling.Builder.class.getMethods()) {
            assertThat(method.getName())
                    .as("Sampling.Builder method %s", method)
                    .isNotEqualTo("factors");
        }
    }

    @Test
    @DisplayName("build() without useCaseFactory is rejected")
    void buildWithoutFactory() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Sampling.<Factors, String, String>builder()
                        .inputs("x")
                        .build())
                .withMessageContaining("useCaseFactory");
    }

    @Test
    @DisplayName("build() without inputs is rejected")
    void buildWithoutInputs() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> Sampling.<Factors, String, String>builder()
                        .useCaseFactory(EchoUseCase::new)
                        .build())
                .withMessageContaining("inputs");
    }

    @Test
    @DisplayName("empty inputs list is rejected")
    void emptyInputsList() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Sampling.<Factors, String, String>builder()
                        .useCaseFactory(EchoUseCase::new)
                        .inputs(java.util.List.of()))
                .withMessageContaining("non-empty");
    }

    @Test
    @DisplayName("samples < 1 is rejected")
    void samplesBelowOne() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().samples(0));
    }

    @Test
    @DisplayName("timeBudget ≤ 0 is rejected")
    void negativeTimeBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().timeBudget(Duration.ZERO));
    }

    @Test
    @DisplayName("tokenBudget ≤ 0 is rejected")
    void negativeTokenBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().tokenBudget(0));
    }

    @Test
    @DisplayName("tokenCharge < 0 is rejected")
    void negativeTokenCharge() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().tokenCharge(-1));
    }

    @Test
    @DisplayName("maxExampleFailures < 0 is rejected")
    void negativeMaxFailures() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> baseBuilder().maxExampleFailures(-1));
    }

    @Test
    @DisplayName("samples(int) wither returns a new sampling with the updated count")
    void samplesWither() {
        Sampling<Factors, String, String> sampling = baseBuilder().samples(100).build();

        Sampling<Factors, String, String> reshaped = sampling.samples(500);

        assertThat(reshaped.samples()).isEqualTo(500);
        assertThat(sampling.samples()).isEqualTo(100);
        assertThat(reshaped.inputs()).isEqualTo(sampling.inputs());
        assertThat(reshaped.useCaseFactory()).isSameAs(sampling.useCaseFactory());
    }

    @Test
    @DisplayName("samples(int) wither rejects non-positive counts")
    void samplesWitherRejectsNonPositive() {
        Sampling<Factors, String, String> sampling = baseBuilder().build();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sampling.samples(0));
    }

    // ── Sampling.of(...) compact form ──────────────────────────────

    @Test
    @DisplayName("Sampling.of(factory, samples, List) builds with defaults for optional knobs")
    void ofWithList() {
        Sampling<Factors, String, String> sampling = Sampling.of(
                EchoUseCase::new,
                250,
                java.util.List.of("alpha", "beta", "gamma"));

        assertThat(sampling.samples()).isEqualTo(250);
        assertThat(sampling.inputs()).containsExactly("alpha", "beta", "gamma");
        assertThat(sampling.timeBudget()).isEmpty();
        assertThat(sampling.tokenBudget()).isEmpty();
        assertThat(sampling.tokenCharge()).isEqualTo(0L);
        assertThat(sampling.budgetPolicy()).isEqualTo(BudgetExhaustionPolicy.FAIL);
        assertThat(sampling.exceptionPolicy()).isEqualTo(ExceptionPolicy.ABORT_TEST);
        assertThat(sampling.maxExampleFailures()).isEqualTo(10);
    }

    @Test
    @DisplayName("Sampling.of(factory, samples, IT...) varargs form is equivalent to the List form")
    void ofWithVarargs() {
        Sampling<Factors, String, String> a = Sampling.of(
                EchoUseCase::new, 100, "x", "y");
        Sampling<Factors, String, String> b = Sampling.of(
                EchoUseCase::new, 100, java.util.List.of("x", "y"));

        assertThat(a.inputs()).isEqualTo(b.inputs());
        assertThat(a.samples()).isEqualTo(b.samples());
    }

    @Test
    @DisplayName("Sampling.of(...) rejects non-positive samples — same contract as the builder")
    void ofRejectsNonPositiveSamples() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Sampling.of(EchoUseCase::new, 0, "x"));
    }

    @Test
    @DisplayName("Sampling.of(...) rejects empty inputs — same contract as the builder")
    void ofRejectsEmptyInputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Sampling.<Factors, String, String>of(
                        EchoUseCase::new, 100, java.util.List.of()));
    }

    // ── InputSupplier integration ──────────────────────────────────

    @Test
    @DisplayName("Sampling.inputsIdentity() exposes the supplier's identity")
    void inputsIdentityExposed() {
        Sampling<Factors, String, String> a = Sampling.of(EchoUseCase::new, 100, "x", "y");
        Sampling<Factors, String, String> b = Sampling.of(EchoUseCase::new, 100, "x", "y");
        Sampling<Factors, String, String> c = Sampling.of(EchoUseCase::new, 100, "x", "z");

        assertThat(a.inputsIdentity()).isEqualTo(b.inputsIdentity());
        assertThat(a.inputsIdentity()).isNotEqualTo(c.inputsIdentity());
        assertThat(a.inputsIdentity()).startsWith("sha256:");
    }

    @Test
    @DisplayName("Sampling.Builder.inputs(InputSupplier) accepts a Tier-2 named supplier")
    void buildsFromNamedSupplier() {
        Sampling<Factors, String, String> sampling = Sampling.<Factors, String, String>builder()
                .useCaseFactory(EchoUseCase::new)
                .inputs(InputSupplier.named("fixture-v1", () -> java.util.List.of("a", "b")))
                .samples(50)
                .build();

        assertThat(sampling.inputsIdentity()).isEqualTo("fixture-v1");
        assertThat(sampling.inputs()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("Sampling.Builder.inputs(InputSupplier) accepts a Tier-3 author-hashed supplier")
    void buildsFromHashedSupplier() {
        Sampling<Factors, String, String> sampling = Sampling.<Factors, String, String>builder()
                .useCaseFactory(EchoUseCase::new)
                .inputs(InputSupplier.hashed(
                        () -> java.util.List.of("a", "b"),
                        inputs -> "custom:" + inputs.size()))
                .samples(50)
                .build();

        assertThat(sampling.inputsIdentity()).isEqualTo("custom:2");
        assertThat(sampling.inputs()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("Sampling.inputSupplier() returns the underlying supplier")
    void inputSupplierAccessor() {
        InputSupplier<String> supplier = InputSupplier.named("fixture-v1", () -> java.util.List.of("a"));
        Sampling<Factors, String, String> sampling = Sampling.<Factors, String, String>builder()
                .useCaseFactory(EchoUseCase::new)
                .inputs(supplier)
                .samples(10)
                .build();

        assertThat(sampling.inputSupplier()).isSameAs(supplier);
    }
}
