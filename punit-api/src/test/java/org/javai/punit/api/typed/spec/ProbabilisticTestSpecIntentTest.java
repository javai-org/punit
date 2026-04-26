package org.javai.punit.api.typed.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.javai.punit.api.TestIntent;
import org.javai.punit.api.typed.Sampling;
import org.javai.punit.api.typed.UseCase;
import org.javai.punit.api.typed.UseCaseOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProbabilisticTestSpec intent")
class ProbabilisticTestSpecIntentTest {

    record Factors(String label) {}

    private static final UseCase<Factors, String, String> ECHO = new UseCase<>() {
        @Override public UseCaseOutcome<String> apply(String input) { return UseCaseOutcome.ok(input); }
    };

    private static Sampling<Factors, String, String> sampling() {
        return Sampling.<Factors, String, String>builder()
                .useCaseFactory(f -> ECHO)
                .inputs("a")
                .samples(10)
                .build();
    }

    @Test
    @DisplayName("default intent is VERIFICATION")
    void defaultIntentIsVerification() {
        ProbabilisticTestSpec<Factors, String, String> spec = ProbabilisticTestSpec
                .testing(sampling(), new Factors("m"))
                .build();

        assertThat(spec.intent()).isEqualTo(TestIntent.VERIFICATION);
    }

    @Test
    @DisplayName(".intent(SMOKE) overrides the default")
    void intentSmokeOverridesDefault() {
        ProbabilisticTestSpec<Factors, String, String> spec = ProbabilisticTestSpec
                .testing(sampling(), new Factors("m"))
                .intent(TestIntent.SMOKE)
                .build();

        assertThat(spec.intent()).isEqualTo(TestIntent.SMOKE);
    }

    @Test
    @DisplayName(".intent(VERIFICATION) is permitted (idempotent with default)")
    void intentVerificationIsPermitted() {
        ProbabilisticTestSpec<Factors, String, String> spec = ProbabilisticTestSpec
                .testing(sampling(), new Factors("m"))
                .intent(TestIntent.VERIFICATION)
                .build();

        assertThat(spec.intent()).isEqualTo(TestIntent.VERIFICATION);
    }

    @Test
    @DisplayName(".intent(null) is rejected")
    void intentNullIsRejected() {
        var builder = ProbabilisticTestSpec.testing(sampling(), new Factors("m"));
        assertThatNullPointerException().isThrownBy(() -> builder.intent(null));
    }
}
