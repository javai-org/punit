package org.javai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.Test;

/**
 * Tests for the transform path on {@link Criteria#transforming}: a
 * criterion that first transforms {@code O} to {@code D}, then
 * evaluates postconditions against the derived value. Transform
 * failure (Outcome.Fail or thrown exception) classifies the sample
 * INCONCLUSIVE with the transform's failure preserved for
 * diagnostics.
 */
class TransformingCriterionTest {

    @Test
    void transformSucceedsAllPassesYieldsPass() {
        Criterion<String> c = Criterion.transforming(
                "parsed-positive",
                s -> Outcome.ok(Integer.parseInt(s)),
                b -> b.ensure("positive", (Integer n) -> n > 0
                        ? Outcome.ok()
                        : Outcome.fail("non-positive", "n=" + n)));

        CriterionSampleResult result = c.evaluate("42");

        assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.PASS);
        assertThat(result.postconditionResults()).hasSize(1);
        assertThat(result.postconditionResults().get(0).passed()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test
    void transformSucceedsAnyPostconditionFailsYieldsFail() {
        Criterion<String> c = Criterion.transforming(
                "parsed-positive",
                s -> Outcome.ok(Integer.parseInt(s)),
                b -> b.ensure("positive", (Integer n) -> n > 0
                        ? Outcome.ok()
                        : Outcome.fail("non-positive", "n=" + n)));

        CriterionSampleResult result = c.evaluate("-5");

        assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.FAIL);
        assertThat(result.postconditionResults()).hasSize(1);
        assertThat(result.postconditionResults().get(0).failed()).isTrue();
        assertThat(result.reason()).isEmpty();
    }

    @Test
    void transformReturnsFailYieldsInconclusiveCarryingTheFailure() {
        Criterion<String> c = Criterion.transforming(
                "parsed-positive",
                s -> Outcome.<Integer>fail("parse-error", "expected an integer"),
                b -> b.ensure("positive", (Integer n) -> Outcome.ok()));

        CriterionSampleResult result = c.evaluate("not-a-number");

        assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.INCONCLUSIVE);
        assertThat(result.postconditionResults()).isEmpty();
        assertThat(result.reason()).isPresent();
        Outcome.Fail<?> failure = result.reason().get();
        assertThat(failure.failure().id().name()).isEqualTo("parse-error");
        assertThat(failure.failure().message()).isEqualTo("expected an integer");
    }

    @Test
    void transformThrowsYieldsInconclusiveCarryingTheExceptionMessage() {
        java.util.function.Function<String, Outcome<Integer>> blowUp =
                s -> { throw new IllegalStateException("kaboom"); };
        Criterion<String> c = Criterion.transforming(
                "parsed-positive",
                blowUp,
                b -> b.ensure("positive", (Integer n) -> Outcome.ok()));

        CriterionSampleResult result = c.evaluate("anything");

        assertThat(result.outcome()).isEqualTo(CriterionSampleOutcome.INCONCLUSIVE);
        assertThat(result.postconditionResults()).isEmpty();
        assertThat(result.reason()).isPresent();
        Outcome.Fail<?> failure = result.reason().get();
        assertThat(failure.failure().message()).isEqualTo("kaboom");
    }

    @Test
    void directCriterionWithoutTransformOperatesOverContractOutput() {
        Criterion<String> c = Criterion.direct(
                "non-empty",
                b -> b.ensure("non-empty", v -> v.isEmpty()
                        ? Outcome.fail("empty", "")
                        : Outcome.ok()));

        CriterionSampleResult pass = c.evaluate("hello");
        assertThat(pass.outcome()).isEqualTo(CriterionSampleOutcome.PASS);

        CriterionSampleResult fail = c.evaluate("");
        assertThat(fail.outcome()).isEqualTo(CriterionSampleOutcome.FAIL);
    }

    @Test
    void factoryValidatesIdAndArguments() {
        assertThatThrownBy(() -> Criterion.direct(null, b -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Criterion.direct("", b -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> Criterion.direct("id", null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> Criterion.transforming(null, s -> Outcome.ok(s), b -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Criterion.transforming("id", null, b -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                Criterion.transforming("id", (String s) -> Outcome.ok(s), null))
                .isInstanceOf(NullPointerException.class);
    }
}
