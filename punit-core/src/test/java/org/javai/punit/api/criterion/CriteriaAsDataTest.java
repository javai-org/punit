package org.javai.punit.api.criterion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.javai.punit.api.criterion.Composite.compose;
import static org.javai.punit.api.criterion.Composite.composeOf;
import static org.javai.punit.api.criterion.Composite.entry;

import java.util.function.Function;
import java.util.function.Predicate;

import org.javai.outcome.Outcome;
import org.javai.punit.api.Contract;
import org.javai.punit.api.ThresholdOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Criteria-as-data — value-form authoring surface")
class CriteriaAsDataTest {

    @Test
    @DisplayName("K=1 bare decl IS a Criteria, lowers to one-entry list with default id 'contract'")
    void bareDeclIsCriteria() {
        Criteria<String> c = Posture.<String>meeting(0.85, ThresholdOrigin.SLA);

        assertThat(c.asList()).hasSize(1);
        assertThat(c.asList().get(0).id()).isEqualTo("contract");
        assertThat(c.asList().get(0).posture().kind())
                .isEqualTo(CriterionPosture.Kind.STATISTICAL_CONTRACTUAL);
        assertThat(c.asList().get(0).posture().threshold().getAsDouble()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("posture chain is value-preserving: .meeting().contractRef().atConfidence() composes via posture")
    void postureChainPreservesValues() {
        CriterionDecl<String> decl = Posture.<String>empirical()
                .atConfidence(0.99)
                .contractRef("doc-v1");

        CriterionPosture p = decl.posture();
        assertThat(p.kind()).isEqualTo(CriterionPosture.Kind.STATISTICAL_EMPIRICAL);
        assertThat(p.confidenceFloor().getAsDouble()).isEqualTo(0.99);
        assertThat(p.contractRef()).contains("doc-v1");
    }

    @Test
    @DisplayName(".where(name, Predicate) — synthesised failure carries the postcondition name")
    void wherePredicateSynthesisesFailure() {
        Predicate<String> isEmpty = String::isEmpty;
        Predicate<String> startsWithA = s -> s.startsWith("a");
        CriterionDecl<String> decl = Posture.<String>meeting(0.85, ThresholdOrigin.SLA)
                .where("non-empty", isEmpty)
                .where("starts-with-a", startsWithA);

        assertThat(decl.postconditions()).hasSize(2);
        assertThat(decl.postconditions().get(0).name()).isEqualTo("non-empty");

        Outcome<?> failResult = decl.postconditions().get(0).check().check("x");
        assertThat(failResult).isInstanceOf(Outcome.Fail.class);
        Outcome.Fail<?> fail = (Outcome.Fail<?>) failResult;
        assertThat(fail.failure().id().name()).isEqualTo("non-empty");
        assertThat(fail.failure().message()).contains("non-empty").contains("returned false").contains("x");
    }

    @Test
    @DisplayName(".where(name, Function<O, Outcome<?>>) — author-supplied message preserved verbatim")
    void whereRichFunctionPreservesMessage() {
        Function<String, Outcome<?>> parseable = v -> Outcome.fail("notJson", "v=" + v);
        CriterionDecl<String> decl = Posture.<String>meeting(0.85, ThresholdOrigin.SLA)
                .where("parseable", parseable);

        Outcome<?> result = decl.postconditions().get(0).check().check("garbage");
        assertThat(result).isInstanceOf(Outcome.Fail.class);
        Outcome.Fail<?> fail = (Outcome.Fail<?>) result;
        assertThat(fail.failure().id().name()).isEqualTo("notJson");
        assertThat(fail.failure().message()).isEqualTo("v=garbage");
    }

    @Test
    @DisplayName("compose(id, decl) — K=1 with explicit id")
    void composeOneExplicitId() {
        Criteria<String> c = compose("payment-success",
                Posture.<String>meeting(0.9999, ThresholdOrigin.SLA));

        assertThat(c.asList()).hasSize(1);
        assertThat(c.asList().get(0).id()).isEqualTo("payment-success");
    }

    @Test
    @DisplayName("compose(...) — K>1 composite, declaration order preserved")
    void composeMany() {
        Criteria<String> c = compose(
                "a", Posture.<String>meeting(0.99, ThresholdOrigin.SLA),
                "b", Posture.<String>meeting(0.95, ThresholdOrigin.SLA),
                "c", Posture.<String>zeroTolerance(ThresholdOrigin.POLICY));

        assertThat(c.asList()).hasSize(3);
        assertThat(c.asList().get(0).id()).isEqualTo("a");
        assertThat(c.asList().get(1).id()).isEqualTo("b");
        assertThat(c.asList().get(2).id()).isEqualTo("c");
    }

    @Test
    @DisplayName("composeOf(entry, entry, ...) — escape hatch for arbitrary K")
    void composeOfEntries() {
        Criteria<String> c = composeOf(
                entry("a", Posture.<String>meeting(0.99, ThresholdOrigin.SLA)),
                entry("b", Posture.<String>meeting(0.95, ThresholdOrigin.SLA)));

        assertThat(c.asList()).hasSize(2);
        assertThat(c.asList().get(0).id()).isEqualTo("a");
        assertThat(c.asList().get(1).id()).isEqualTo("b");
    }

    @Test
    @DisplayName("duplicate criterion ids in compose(...) rejected at construction")
    void duplicateIdsRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> compose(
                        "a", Posture.<String>meeting(0.99, ThresholdOrigin.SLA),
                        "a", Posture.<String>meeting(0.95, ThresholdOrigin.SLA)))
                .withMessageContaining("duplicate");
    }

    @Test
    @DisplayName("Contract.criteria() default returns empty; effectiveCriteria() falls back to K=1 default")
    void defaultContractFallsBackToK1Default() {
        Contract<Object, String> c = new Contract<>() {
            @Override public Outcome<String> invoke(Object input,
                    org.javai.punit.api.TokenTracker t) {
                return Outcome.ok("ok");
            }
        };

        assertThat(c.criteria().isEmpty()).isTrue();
        assertThat(c.effectiveCriteria()).hasSize(1);
    }

    @Test
    @DisplayName("Contract.criteria() value-form: bare decl drives effectiveCriteria")
    void valueFormDrivesEffectiveCriteria() {
        Contract<Object, String> c = new Contract<>() {
            @Override public Outcome<String> invoke(Object input,
                    org.javai.punit.api.TokenTracker t) {
                return Outcome.ok("ok");
            }
            @Override public Criteria<String> criteria() {
                return Posture.<String>meeting(0.85, ThresholdOrigin.SLA)
                        .contractRef("doc-v1");
            }
        };

        assertThat(c.effectiveCriteria()).hasSize(1);
        assertThat(c.effectiveCriteria().get(0).id()).isEqualTo("contract");
        assertThat(c.effectiveCriteria().get(0).posture().contractRef()).contains("doc-v1");
    }

    @Test
    @DisplayName("Contract that declares both value-form and builder-form is rejected at effectiveCriteria()")
    void coexistenceRejected() {
        Contract<Object, String> c = new Contract<>() {
            @Override public Outcome<String> invoke(Object input,
                    org.javai.punit.api.TokenTracker t) {
                return Outcome.ok("ok");
            }
            @Override public Criteria<String> criteria() {
                return Posture.<String>meeting(0.85, ThresholdOrigin.SLA);
            }
            @Override public void criteria(CriteriaBuilder<String> b) {
                b.addCriterion("other", pb -> { });
            }
        };

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(c::effectiveCriteria)
                .withMessageContaining("value-form")
                .withMessageContaining("builder-form");
    }
}
