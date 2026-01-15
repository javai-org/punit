package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.HashMap;
import java.util.Map;
import org.javai.punit.api.BudgetExhaustedBehavior;
import org.javai.punit.api.ExceptionHandling;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.api.ThresholdOrigin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfigurationResolver}.
 */
class ConfigurationResolverTest {

    private Map<String, String> envVars;
    private ConfigurationResolver resolver;

    @BeforeEach
    void setUp() {
        envVars = new HashMap<>();
        resolver = new TestableConfigurationResolver(envVars);
        
        // Clear any system properties that might interfere
        System.clearProperty(ConfigurationResolver.PROP_SAMPLES);
        System.clearProperty(ConfigurationResolver.PROP_MIN_PASS_RATE);
        System.clearProperty(ConfigurationResolver.PROP_SAMPLES_MULTIPLIER);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(ConfigurationResolver.PROP_SAMPLES);
        System.clearProperty(ConfigurationResolver.PROP_MIN_PASS_RATE);
        System.clearProperty(ConfigurationResolver.PROP_SAMPLES_MULTIPLIER);
    }

    @Test
    void usesAnnotationValuesWhenNoOverrides() {
        ProbabilisticTest annotation = createAnnotation(50, 0.80);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(50);
        assertThat(config.minPassRate()).isEqualTo(0.80);
        assertThat(config.appliedMultiplier()).isEqualTo(1.0);
    }

    @Test
    void systemPropertyOverridesAnnotation() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES, "200");
        System.setProperty(ConfigurationResolver.PROP_MIN_PASS_RATE, "0.75");
        
        ProbabilisticTest annotation = createAnnotation(50, 0.80);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(200);
        assertThat(config.minPassRate()).isEqualTo(0.75);
    }

    @Test
    void envVarOverridesAnnotation() {
        envVars.put(ConfigurationResolver.ENV_SAMPLES, "150");
        envVars.put(ConfigurationResolver.ENV_MIN_PASS_RATE, "0.60");
        
        ProbabilisticTest annotation = createAnnotation(50, 0.80);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(150);
        assertThat(config.minPassRate()).isEqualTo(0.60);
    }

    @Test
    void systemPropertyTakesPrecedenceOverEnvVar() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES, "300");
        envVars.put(ConfigurationResolver.ENV_SAMPLES, "200");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(300);
    }

    @Test
    void multiplierScalesSamples() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES_MULTIPLIER, "2.0");
        
        ProbabilisticTest annotation = createAnnotation(50, 0.95);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(100); // 50 * 2.0 = 100
        assertThat(config.appliedMultiplier()).isEqualTo(2.0);
        assertThat(config.hasMultiplier()).isTrue();
    }

    @Test
    void multiplierCanReduceSamples() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES_MULTIPLIER, "0.1");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(10); // 100 * 0.1 = 10
    }

    @Test
    void multiplierEnforcesMininumOneSample() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES_MULTIPLIER, "0.001");
        
        ProbabilisticTest annotation = createAnnotation(10, 0.95);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(1); // max(1, round(10 * 0.001)) = 1
    }

    @Test
    void multiplierFromEnvVar() {
        envVars.put(ConfigurationResolver.ENV_SAMPLES_MULTIPLIER, "0.5");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(50); // 100 * 0.5 = 50
    }

    @Test
    void invalidSamplesSystemPropertyThrowsException() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES, "not-a-number");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        assertThatThrownBy(() -> resolver.resolve(annotation, "testMethod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("punit.samples")
                .hasMessageContaining("not-a-number");
    }

    @Test
    void invalidMinPassRateEnvVarThrowsException() {
        envVars.put(ConfigurationResolver.ENV_MIN_PASS_RATE, "abc");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        assertThatThrownBy(() -> resolver.resolve(annotation, "testMethod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUNIT_MIN_PASS_RATE")
                .hasMessageContaining("abc");
    }

    @Test
    void minPassRateOutOfRangeThrowsException() {
        System.setProperty(ConfigurationResolver.PROP_MIN_PASS_RATE, "1.5");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        assertThatThrownBy(() -> resolver.resolve(annotation, "testMethod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPassRate")
                .hasMessageContaining("1.5");
    }

    @Test
    void samplesZeroOrNegativeThrowsException() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES, "0");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        assertThatThrownBy(() -> resolver.resolve(annotation, "testMethod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("samples")
                .hasMessageContaining("0");
    }

    @Test
    void hasMultiplierReturnsFalseWhenNoMultiplierApplied() {
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.hasMultiplier()).isFalse();
        assertThat(config.appliedMultiplier()).isEqualTo(1.0);
    }

    @Test
    void handlesWhitespaceInPropertyValues() {
        System.setProperty(ConfigurationResolver.PROP_SAMPLES, "  75  ");
        
        ProbabilisticTest annotation = createAnnotation(100, 0.95);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.samples()).isEqualTo(75);
    }

    // ========== Helper Methods ==========

    private ProbabilisticTest createAnnotation(int samples, double minPassRate) {
        return new ProbabilisticTest() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ProbabilisticTest.class;
            }

            @Override
            public int samples() {
                return samples;
            }

            @Override
            public double minPassRate() {
                return minPassRate;
            }

            @Override
            public long timeBudgetMs() {
                return 0;
            }

            @Override
            public int tokenCharge() {
                return 0;
            }

            @Override
            public long tokenBudget() {
                return 0;
            }

            @Override
            public BudgetExhaustedBehavior onBudgetExhausted() {
                return BudgetExhaustedBehavior.FAIL;
            }

            @Override
            public ExceptionHandling onException() {
                return ExceptionHandling.FAIL_SAMPLE;
            }

            @Override
            public int maxExampleFailures() {
                return 5;
            }

            @Override
            public Class<?> useCase() {
                return Void.class;
            }

            @Override
            public double thresholdConfidence() {
                return Double.NaN;
            }

            @Override
            public double confidence() {
                return Double.NaN;
            }

            @Override
            public double minDetectableEffect() {
                return Double.NaN;
            }

            @Override
            public double power() {
                return Double.NaN;
            }

            @Override
            public boolean transparentStats() {
                return false;
            }

            @Override
            public ThresholdOrigin thresholdOrigin() {
                return ThresholdOrigin.UNSPECIFIED;
            }

            @Override
            public String contractRef() {
                return "";
            }
        };
    }

    @Test
    void returnsNaNMinPassRateWhenNoExplicitThreshold() {
        // When minPassRate is NaN and there's no useCase, ConfigurationResolver returns NaN.
        // The validation is done later by ProbabilisticTestValidator after baseline selection.
        ProbabilisticTest annotation = createAnnotationWithNaNMinPassRate(100);
        
        ConfigurationResolver.ResolvedConfiguration config = resolver.resolve(annotation, "testMethod");
        
        assertThat(config.minPassRate()).isNaN();
        assertThat(config.samples()).isEqualTo(100);
    }

    /**
     * Creates a mock annotation with NaN minPassRate (no spec reference).
     */
    private ProbabilisticTest createAnnotationWithNaNMinPassRate(int samples) {
        return new ProbabilisticTest() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ProbabilisticTest.class;
            }

            @Override
            public int samples() {
                return samples;
            }

            @Override
            public double minPassRate() {
                return Double.NaN;  // NaN to test default behavior
            }

            @Override
            public long timeBudgetMs() {
                return 0;
            }

            @Override
            public int tokenCharge() {
                return 0;
            }

            @Override
            public long tokenBudget() {
                return 0;
            }

            @Override
            public BudgetExhaustedBehavior onBudgetExhausted() {
                return BudgetExhaustedBehavior.FAIL;
            }

            @Override
            public ExceptionHandling onException() {
                return ExceptionHandling.FAIL_SAMPLE;
            }

            @Override
            public int maxExampleFailures() {
                return 5;
            }

            @Override
            public Class<?> useCase() {
                return Void.class;
            }

            @Override
            public double thresholdConfidence() {
                return Double.NaN;
            }

            @Override
            public double confidence() {
                return Double.NaN;
            }

            @Override
            public double minDetectableEffect() {
                return Double.NaN;
            }

            @Override
            public double power() {
                return Double.NaN;
            }

            @Override
            public boolean transparentStats() {
                return false;
            }

            @Override
            public ThresholdOrigin thresholdOrigin() {
                return ThresholdOrigin.UNSPECIFIED;
            }

            @Override
            public String contractRef() {
                return "";
            }
        };
    }

    /**
     * Testable subclass that allows mocking environment variables.
     */
    private static class TestableConfigurationResolver extends ConfigurationResolver {
        private final Map<String, String> envVars;

        TestableConfigurationResolver(Map<String, String> envVars) {
            this.envVars = envVars;
        }

        @Override
        protected String getEnvironmentVariable(String name) {
            return envVars.get(name);
        }
    }
}

