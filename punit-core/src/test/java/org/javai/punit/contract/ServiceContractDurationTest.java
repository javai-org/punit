package org.javai.punit.contract;

import org.javai.outcome.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ServiceContract duration constraint")
class ServiceContractDurationTest {

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("builds contract without duration constraint")
        void buildsWithoutDurationConstraint() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check", "empty") : Outcome.ok())
                    .build();

            assertThat(contract.durationConstraint()).isEmpty();
        }

        @Test
        @DisplayName("builds contract with duration constraint using default description")
        void buildsWithDurationConstraintDefaultDescription() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check", "empty") : Outcome.ok())
                    .ensureDurationBelow(Duration.ofMillis(500))
                    .build();

            assertThat(contract.durationConstraint()).isPresent();
            assertThat(contract.durationConstraint().get().maxDuration()).isEqualTo(Duration.ofMillis(500));
            assertThat(contract.durationConstraint().get().description()).isEqualTo("Duration below 500ms");
        }

        @Test
        @DisplayName("builds contract with duration constraint using custom description")
        void buildsWithDurationConstraintCustomDescription() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> s.isEmpty() ? Outcome.fail("check", "empty") : Outcome.ok())
                    .ensureDurationBelow("API response time SLA", Duration.ofMillis(200))
                    .build();

            assertThat(contract.durationConstraint()).isPresent();
            assertThat(contract.durationConstraint().get().description()).isEqualTo("API response time SLA");
        }

        @Test
        @DisplayName("allows duration constraint after derive chain")
        void allowsDurationConstraintAfterDeriveChain() {
            ServiceContract<Void, String> contract = ServiceContract
                    .<Void, String>define()
                    .derive("Parse value", s -> Outcome.ok(Integer.parseInt(s)))
                        .ensure("Positive", i -> i > 0 ? Outcome.ok() : Outcome.fail("check", "not positive"))
                    .ensureDurationBelow(Duration.ofMillis(100))
                    .build();

            assertThat(contract.durationConstraint()).isPresent();
            assertThat(contract.derivations()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("postcondition count")
    class PostconditionCount {

        @Test
        @DisplayName("duration constraint does not affect postcondition count")
        void durationConstraintNotInPostconditionCount() {
            ServiceContract<Void, String> withoutDuration = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .build();

            ServiceContract<Void, String> withDuration = ServiceContract
                    .<Void, String>define()
                    .ensure("Not empty", s -> Outcome.ok())
                    .ensureDurationBelow(Duration.ofMillis(500))
                    .build();

            // Duration is evaluated independently, not as a postcondition
            assertThat(withDuration.postconditionCount()).isEqualTo(withoutDuration.postconditionCount());
        }
    }
}
