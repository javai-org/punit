package org.javai.punit.internal.engine.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.javai.punit.api.FactorBundle;
import org.javai.punit.api.PercentileKey;
import org.javai.punit.api.TestIntent;
import org.javai.punit.api.spec.BaselineProvider;
import org.javai.punit.api.spec.BaselineStatistics;
import org.javai.punit.api.spec.PercentileLatency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Latency-criterion preflight feasibility")
class LatencyFeasibilityTest {

    private static final String CONTRACT_ID = "feasibility-test";
    private static final FactorBundle EMPTY_FACTORS = FactorBundle.empty();
    private static final BaselineProvider NULL_PROVIDER = new BaselineProvider() {
        @Override
        public <S extends BaselineStatistics> java.util.Optional<S> baselineFor(
                String id, FactorBundle factors, String name, Class<S> type,
                org.javai.punit.api.covariate.CovariateProfile profile,
                java.util.List<org.javai.punit.api.covariate.Covariate> declarations) {
            return java.util.Optional.empty();
        }
        @Override
        public java.util.Optional<String> baselineInputsIdentityFor(
                String id, FactorBundle factors,
                org.javai.punit.api.covariate.CovariateProfile profile,
                java.util.List<org.javai.punit.api.covariate.Covariate> declarations) {
            return java.util.Optional.empty();
        }
    };

    @Test
    @DisplayName("samples below floor for P95 produces a warning")
    void warnsBelowFloorForP95() {
        List<String> warnings = Feasibility.check(
                10,
                PercentileLatency.<Integer>empirical(PercentileKey.P95),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("percentile-latency")
                .contains("P95")
                .contains("10 samples")
                .contains("at least 20");
    }

    @Test
    @DisplayName("samples at the floor emits no warning")
    void silentAtFloorForP95() {
        List<String> warnings = Feasibility.check(
                20,
                PercentileLatency.<Integer>empirical(PercentileKey.P95),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER);
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("multiple percentiles produce per-percentile warnings")
    void warnsForEachAssertedPercentileBelowFloor() {
        List<String> warnings = Feasibility.check(
                15,
                PercentileLatency.<Integer>empirical(PercentileKey.P95, PercentileKey.P99),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER);

        assertThat(warnings).hasSize(2);
        assertThat(warnings).anyMatch(w -> w.contains("P95") && w.contains("at least 20"));
        assertThat(warnings).anyMatch(w -> w.contains("P99") && w.contains("at least 100"));
    }

    @Test
    @DisplayName("P50 has floor of 1 — never warns above zero samples")
    void p50FloorIsOne() {
        List<String> warnings = Feasibility.check(
                1,
                PercentileLatency.<Integer>empirical(PercentileKey.P50),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.VERIFICATION, NULL_PROVIDER);
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("SMOKE intent silences the warning")
    void smokeIntentSilences() {
        List<String> warnings = Feasibility.check(
                5,
                PercentileLatency.<Integer>empirical(PercentileKey.P99),
                CONTRACT_ID, EMPTY_FACTORS, TestIntent.SMOKE, NULL_PROVIDER);
        assertThat(warnings).isEmpty();
    }
}
