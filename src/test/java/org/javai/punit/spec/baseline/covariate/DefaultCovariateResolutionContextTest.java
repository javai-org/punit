package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DefaultCovariateResolutionContext}.
 */
@DisplayName("DefaultCovariateResolutionContext")
class DefaultCovariateResolutionContextTest {

    @Nested
    @DisplayName("now()")
    class NowTests {

        @Test
        @DisplayName("should return injected time when set")
        void shouldReturnInjectedTimeWhenSet() {
            var fixedTime = Instant.parse("2026-01-10T14:30:00Z");
            var context = DefaultCovariateResolutionContext.builder()
                .now(fixedTime)
                .build();
            
            assertThat(context.now()).isEqualTo(fixedTime);
        }

        @Test
        @DisplayName("should return current time when not set")
        void shouldReturnCurrentTimeWhenNotSet() {
            var before = Instant.now();
            var context = DefaultCovariateResolutionContext.forNow();
            var after = Instant.now();
            
            assertThat(context.now()).isBetween(before, after.plusMillis(1));
        }
    }

    @Nested
    @DisplayName("experimentTiming()")
    class ExperimentTimingTests {

        @Test
        @DisplayName("should return empty when not set")
        void shouldReturnEmptyWhenNotSet() {
            var context = DefaultCovariateResolutionContext.forNow();
            
            assertThat(context.experimentStartTime()).isEmpty();
            assertThat(context.experimentEndTime()).isEmpty();
        }

        @Test
        @DisplayName("should return injected timing")
        void shouldReturnInjectedTiming() {
            var start = Instant.parse("2026-01-10T14:30:00Z");
            var end = Instant.parse("2026-01-10T14:45:00Z");
            
            var context = DefaultCovariateResolutionContext.builder()
                .experimentTiming(start, end)
                .build();
            
            assertThat(context.experimentStartTime()).contains(start);
            assertThat(context.experimentEndTime()).contains(end);
        }
    }

    @Nested
    @DisplayName("systemTimezone()")
    class SystemTimezoneTests {

        @Test
        @DisplayName("should return injected timezone when set")
        void shouldReturnInjectedTimezoneWhenSet() {
            var timezone = ZoneId.of("Europe/London");
            var context = DefaultCovariateResolutionContext.builder()
                .systemTimezone(timezone)
                .build();
            
            assertThat(context.systemTimezone()).isEqualTo(timezone);
        }

        @Test
        @DisplayName("should return system default when not set")
        void shouldReturnSystemDefaultWhenNotSet() {
            var context = DefaultCovariateResolutionContext.forNow();
            
            assertThat(context.systemTimezone()).isEqualTo(ZoneId.systemDefault());
        }
    }

    @Nested
    @DisplayName("getSystemProperty()")
    class GetSystemPropertyTests {

        @Test
        @DisplayName("should return value for existing property")
        void shouldReturnValueForExistingProperty() {
            var context = DefaultCovariateResolutionContext.forNow();
            
            // java.version is always set
            assertThat(context.getSystemProperty("java.version")).isPresent();
        }

        @Test
        @DisplayName("should return empty for missing property")
        void shouldReturnEmptyForMissingProperty() {
            var context = DefaultCovariateResolutionContext.forNow();
            
            assertThat(context.getSystemProperty("nonexistent.property.xyz")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPunitEnvironment()")
    class GetPunitEnvironmentTests {

        @Test
        @DisplayName("should return value for existing key")
        void shouldReturnValueForExistingKey() {
            var context = DefaultCovariateResolutionContext.builder()
                .punitEnvironment(Map.of("region", "EU", "env", "prod"))
                .build();
            
            assertThat(context.getPunitEnvironment("region")).contains("EU");
            assertThat(context.getPunitEnvironment("env")).contains("prod");
        }

        @Test
        @DisplayName("should return empty for missing key")
        void shouldReturnEmptyForMissingKey() {
            var context = DefaultCovariateResolutionContext.builder()
                .punitEnvironment(Map.of("region", "EU"))
                .build();
            
            assertThat(context.getPunitEnvironment("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("should return empty when no environment set")
        void shouldReturnEmptyWhenNoEnvironmentSet() {
            var context = DefaultCovariateResolutionContext.forNow();
            
            assertThat(context.getPunitEnvironment("anything")).isEmpty();
        }
    }
}

