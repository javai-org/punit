package org.javai.punit.controls.pacing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.javai.punit.api.Pacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PacingResolver}.
 */
class PacingResolverTest {

    private TestablePacingResolver resolver;
    private Map<String, String> envVars;

    @BeforeEach
    void setUp() {
        envVars = new HashMap<>();
        resolver = new TestablePacingResolver(envVars);
    }

    @AfterEach
    void tearDown() {
        // Clear any system properties set during tests
        System.clearProperty(PacingResolver.PROP_MAX_RPS);
        System.clearProperty(PacingResolver.PROP_MAX_RPM);
        System.clearProperty(PacingResolver.PROP_MAX_RPH);
        System.clearProperty(PacingResolver.PROP_MAX_CONCURRENT);
        System.clearProperty(PacingResolver.PROP_MIN_MS_PER_SAMPLE);
    }

    @Nested
    @DisplayName("Resolution from Annotation")
    class AnnotationResolutionTests {

        @Test
        @DisplayName("Returns noPacing when annotation is null")
        void nullAnnotation_returnsNoPacing() {
            PacingConfiguration config = resolver.resolve((Pacing) null, 100, 0);

            assertThat(config.hasPacing()).isFalse();
        }

        @Test
        @DisplayName("Resolves all values from annotation")
        void resolvesFromAnnotation() {
            Pacing pacing = createPacing(2.0, 60, 3600, 3, 500);
            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.maxRequestsPerSecond()).isEqualTo(2.0);
            assertThat(config.maxRequestsPerMinute()).isEqualTo(60);
            assertThat(config.maxRequestsPerHour()).isEqualTo(3600);
            assertThat(config.maxConcurrentRequests()).isEqualTo(3);
            assertThat(config.minMsPerSample()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("System Property Override")
    class SystemPropertyTests {

        @Test
        @DisplayName("System property overrides annotation for maxRps")
        void systemPropertyOverrides_maxRps() {
            System.setProperty(PacingResolver.PROP_MAX_RPS, "5.0");
            Pacing pacing = createPacing(2.0, 0, 0, 0, 0);

            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.maxRequestsPerSecond()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("System property overrides annotation for maxRpm")
        void systemPropertyOverrides_maxRpm() {
            System.setProperty(PacingResolver.PROP_MAX_RPM, "120");
            Pacing pacing = createPacing(0, 60, 0, 0, 0);

            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.maxRequestsPerMinute()).isEqualTo(120);
        }

        @Test
        @DisplayName("System property overrides annotation for maxRph")
        void systemPropertyOverrides_maxRph() {
            System.setProperty(PacingResolver.PROP_MAX_RPH, "7200");
            Pacing pacing = createPacing(0, 0, 3600, 0, 0);

            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.maxRequestsPerHour()).isEqualTo(7200);
        }

        @Test
        @DisplayName("System property overrides annotation for maxConcurrent")
        void systemPropertyOverrides_maxConcurrent() {
            System.setProperty(PacingResolver.PROP_MAX_CONCURRENT, "10");
            Pacing pacing = createPacing(0, 0, 0, 3, 0);

            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.maxConcurrentRequests()).isEqualTo(10);
        }

        @Test
        @DisplayName("System property overrides annotation for minMsPerSample")
        void systemPropertyOverrides_minMs() {
            System.setProperty(PacingResolver.PROP_MIN_MS_PER_SAMPLE, "1000");
            Pacing pacing = createPacing(0, 0, 0, 0, 500);

            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.minMsPerSample()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Invalid system property throws exception")
        void invalidSystemProperty_throws() {
            System.setProperty(PacingResolver.PROP_MAX_RPM, "not-a-number");
            Pacing pacing = createPacing(0, 60, 0, 0, 0);

            assertThatThrownBy(() -> resolver.resolve(pacing, 100, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid value for system property");
        }
    }

    @Nested
    @DisplayName("Environment Variable Override")
    class EnvironmentVariableTests {

        @Test
        @DisplayName("Environment variable overrides annotation for maxRpm")
        void envVarOverrides_maxRpm() {
            envVars.put(PacingResolver.ENV_MAX_RPM, "180");
            Pacing pacing = createPacing(0, 60, 0, 0, 0);

            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.maxRequestsPerMinute()).isEqualTo(180);
        }

        @Test
        @DisplayName("System property takes precedence over environment variable")
        void systemProperty_precedesEnvVar() {
            System.setProperty(PacingResolver.PROP_MAX_RPM, "240");
            envVars.put(PacingResolver.ENV_MAX_RPM, "180");
            Pacing pacing = createPacing(0, 60, 0, 0, 0);

            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.maxRequestsPerMinute()).isEqualTo(240);
        }

        @Test
        @DisplayName("Invalid environment variable throws exception")
        void invalidEnvVar_throws() {
            envVars.put(PacingResolver.ENV_MAX_CONCURRENT, "invalid");
            Pacing pacing = createPacing(0, 0, 0, 3, 0);

            assertThatThrownBy(() -> resolver.resolve(pacing, 100, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid value for environment variable");
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Negative maxRps throws exception")
        void negativeRps_throws() {
            System.setProperty(PacingResolver.PROP_MAX_RPS, "-1");

            assertThatThrownBy(() -> resolver.resolve((Pacing) null, 100, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxRequestsPerSecond must be >= 0");
        }

        @Test
        @DisplayName("Negative maxRpm throws exception")
        void negativeRpm_throws() {
            System.setProperty(PacingResolver.PROP_MAX_RPM, "-60");

            assertThatThrownBy(() -> resolver.resolve((Pacing) null, 100, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxRequestsPerMinute must be >= 0");
        }

        @Test
        @DisplayName("Negative maxConcurrent throws exception")
        void negativeConcurrent_throws() {
            System.setProperty(PacingResolver.PROP_MAX_CONCURRENT, "-5");

            assertThatThrownBy(() -> resolver.resolve((Pacing) null, 100, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxConcurrentRequests must be >= 0");
        }

        @Test
        @DisplayName("Negative minMsPerSample throws exception")
        void negativeMinMs_throws() {
            System.setProperty(PacingResolver.PROP_MIN_MS_PER_SAMPLE, "-100");

            assertThatThrownBy(() -> resolver.resolve((Pacing) null, 100, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minMsPerSample must be >= 0");
        }
    }

    @Nested
    @DisplayName("Method Resolution")
    class MethodResolutionTests {

        @Test
        @DisplayName("Resolves pacing from method with @Pacing annotation")
        void resolvesFromMethod() throws NoSuchMethodException {
            Method method = TestClass.class.getMethod("methodWithPacing");
            PacingConfiguration config = resolver.resolve(method, 100);

            assertThat(config.hasPacing()).isTrue();
            assertThat(config.maxRequestsPerMinute()).isEqualTo(60);
        }

        @Test
        @DisplayName("Returns noPacing for method without @Pacing annotation")
        void noPacing_forMethodWithoutAnnotation() throws NoSuchMethodException {
            Method method = TestClass.class.getMethod("methodWithoutPacing");
            PacingConfiguration config = resolver.resolve(method, 100);

            assertThat(config.hasPacing()).isFalse();
        }
    }

    @Nested
    @DisplayName("Execution Plan Computation")
    class ExecutionPlanTests {

        @Test
        @DisplayName("Computes effective delay from resolved constraints")
        void computesEffectiveDelay() {
            Pacing pacing = createPacing(0, 60, 0, 0, 0);
            PacingConfiguration config = resolver.resolve(pacing, 100, 0);

            assertThat(config.effectiveMinDelayMs()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Computes estimated duration")
        void computesEstimatedDuration() {
            Pacing pacing = createPacing(0, 60, 0, 0, 0);
            PacingConfiguration config = resolver.resolve(pacing, 200, 0);

            // 200 samples at 1 RPS = 200 seconds
            assertThat(config.estimatedDurationMs()).isEqualTo(200_000);
        }

        @Test
        @DisplayName("Uses estimated latency when provided")
        void usesEstimatedLatency() {
            Pacing pacing = createPacing(0, 60, 0, 5, 0);
            PacingConfiguration config = resolver.resolve(pacing, 100, 2000);

            // With latency info, concurrency can be adjusted
            assertThat(config.effectiveConcurrency()).isGreaterThanOrEqualTo(1);
        }
    }

    // Test class with annotated methods
    public static class TestClass {
        @Pacing(maxRequestsPerMinute = 60)
        public void methodWithPacing() {}

        public void methodWithoutPacing() {}
    }

    // Testable subclass that allows mocking environment variables
    private static class TestablePacingResolver extends PacingResolver {
        private final Map<String, String> envVars;

        TestablePacingResolver(Map<String, String> envVars) {
            this.envVars = envVars;
        }

        @Override
        protected String getEnvironmentVariable(String name) {
            return envVars.get(name);
        }
    }

    // Helper to create a Pacing annotation instance
    private Pacing createPacing(double rps, double rpm, double rph, int concurrent, long minMs) {
        return new Pacing() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Pacing.class;
            }

            @Override
            public double maxRequestsPerSecond() {
                return rps;
            }

            @Override
            public double maxRequestsPerMinute() {
                return rpm;
            }

            @Override
            public double maxRequestsPerHour() {
                return rph;
            }

            @Override
            public int maxConcurrentRequests() {
                return concurrent;
            }

            @Override
            public long minMsPerSample() {
                return minMs;
            }
        };
    }
}

