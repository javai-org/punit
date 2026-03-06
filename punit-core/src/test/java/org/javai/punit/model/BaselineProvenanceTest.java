package org.javai.punit.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BaselineProvenance}.
 */
@DisplayName("BaselineProvenance")
class BaselineProvenanceTest {

    @Nested
    @DisplayName("toHumanReadable()")
    class ToHumanReadableTests {

        @Test
        @DisplayName("should format with all fields")
        void shouldFormatWithAllFields() {
            var provenance = new BaselineProvenance(
                "ShoppingUseCase-a1b2.yaml",
                "a1b2c3d4",
                Instant.parse("2026-01-10T14:30:00Z"),
                1000,
                CovariateProfile.empty()
            );
            
            var readable = provenance.toHumanReadable();
            
            assertThat(readable).contains("ShoppingUseCase-a1b2.yaml");
            assertThat(readable).contains("2026-01-10");
            assertThat(readable).contains("1000 samples");
        }

        @Test
        @DisplayName("should handle null generatedAt")
        void shouldHandleNullGeneratedAt() {
            var provenance = new BaselineProvenance(
                "test.yaml",
                "footprint",
                null,
                100,
                null
            );
            
            var readable = provenance.toHumanReadable();
            
            assertThat(readable).contains("test.yaml");
            assertThat(readable).contains("100 samples");
        }
    }

    @Nested
    @DisplayName("toReportProperties()")
    class ToReportPropertiesTests {

        @Test
        @DisplayName("should include basic properties")
        void shouldIncludeBasicProperties() {
            var provenance = new BaselineProvenance(
                "test.yaml",
                "a1b2c3d4",
                Instant.parse("2026-01-10T14:30:00Z"),
                500,
                CovariateProfile.empty()
            );
            
            var props = provenance.toReportProperties();
            
            assertThat(props).containsEntry("punit.baseline.filename", "test.yaml");
            assertThat(props).containsEntry("punit.baseline.footprint", "a1b2c3d4");
            assertThat(props).containsEntry("punit.baseline.samples", "500");
            assertThat(props).containsKey("punit.baseline.generatedAt");
        }

        @Test
        @DisplayName("should include covariate properties")
        void shouldIncludeCovariateProperties() {
            var profile = CovariateProfile.builder()
                .put("region", "EU")
                .put("timezone", "Europe/London")
                .build();
            
            var provenance = new BaselineProvenance(
                "test.yaml",
                "footprint",
                null,
                100,
                profile
            );
            
            var props = provenance.toReportProperties();
            
            assertThat(props).containsEntry("punit.baseline.covariate.region", "EU");
            assertThat(props).containsEntry("punit.baseline.covariate.timezone", "Europe/London");
        }
    }
}

