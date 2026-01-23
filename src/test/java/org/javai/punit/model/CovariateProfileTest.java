package org.javai.punit.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CovariateProfile}.
 */
@DisplayName("CovariateProfile")
class CovariateProfileTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("empty() should create an empty profile")
        void emptyShouldCreateEmptyProfile() {
            var profile = CovariateProfile.empty();
            
            assertThat(profile.isEmpty()).isTrue();
            assertThat(profile.size()).isEqualTo(0);
            assertThat(profile.orderedKeys()).isEmpty();
        }

        @Test
        @DisplayName("builder should preserve insertion order")
        void builderShouldPreserveInsertionOrder() {
            var profile = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Mo-Fr")
                .put("timezone", "Europe/London")
                .put("region", "EU")
                .build();
            
            assertThat(profile.orderedKeys())
                .containsExactly("weekday_vs_weekend", "timezone", "region");
        }

        @Test
        @DisplayName("profile should be immutable")
        void profileShouldBeImmutable() {
            var profile = CovariateProfile.builder()
                .put("key1", "value1")
                .build();
            
            assertThat(profile.asMap()).isUnmodifiable();
            assertThat(profile.orderedKeys()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("should return value for existing key")
        void shouldReturnValueForExistingKey() {
            var profile = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            
            assertThat(profile.get("region")).isEqualTo(new CovariateValue.StringValue("EU"));
        }

        @Test
        @DisplayName("should return null for missing key")
        void shouldReturnNullForMissingKey() {
            var profile = CovariateProfile.empty();
            
            assertThat(profile.get("nonexistent")).isNull();
        }
    }

    @Nested
    @DisplayName("computeHash()")
    class ComputeHashTests {

        @Test
        @DisplayName("should return empty string for empty profile")
        void shouldReturnEmptyStringForEmptyProfile() {
            var profile = CovariateProfile.empty();
            
            assertThat(profile.computeHash()).isEmpty();
        }

        @Test
        @DisplayName("hash should be stable across calls")
        void hashShouldBeStableAcrossCalls() {
            var profile = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Mo-Fr")
                .put("region", "EU")
                .build();
            
            var hash1 = profile.computeHash();
            var hash2 = profile.computeHash();
            
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("hash should be 8 characters")
        void hashShouldBe8Characters() {
            var profile = CovariateProfile.builder()
                .put("key", "value")
                .build();
            
            assertThat(profile.computeHash()).hasSize(8);
        }

        @Test
        @DisplayName("different values should produce different hashes")
        void differentValuesShouldProduceDifferentHashes() {
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("region", "US")
                .build();
            
            assertThat(profile1.computeHash()).isNotEqualTo(profile2.computeHash());
        }

        @Test
        @DisplayName("different keys should produce different hashes")
        void differentKeysShouldProduceDifferentHashes() {
            var profile1 = CovariateProfile.builder()
                .put("region", "EU")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("zone", "EU")
                .build();
            
            assertThat(profile1.computeHash()).isNotEqualTo(profile2.computeHash());
        }

        @Test
        @DisplayName("hash should incorporate ordering")
        void hashShouldIncorporateOrdering() {
            var profile1 = CovariateProfile.builder()
                .put("a", "1")
                .put("b", "2")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("b", "2")
                .put("a", "1")
                .build();
            
            // Different ordering = different hash
            assertThat(profile1.computeHash()).isNotEqualTo(profile2.computeHash());
        }
    }

    @Nested
    @DisplayName("computeValueHashes()")
    class ComputeValueHashesTests {

        @Test
        @DisplayName("should return empty list for empty profile")
        void shouldReturnEmptyListForEmptyProfile() {
            var profile = CovariateProfile.empty();
            
            assertThat(profile.computeValueHashes()).isEmpty();
        }

        @Test
        @DisplayName("should return one hash per covariate")
        void shouldReturnOneHashPerCovariate() {
            var profile = CovariateProfile.builder()
                .put("a", "1")
                .put("b", "2")
                .put("c", "3")
                .build();
            
            assertThat(profile.computeValueHashes()).hasSize(3);
        }

        @Test
        @DisplayName("each hash should be 4 characters")
        void eachHashShouldBe4Characters() {
            var profile = CovariateProfile.builder()
                .put("key", "value")
                .build();
            
            assertThat(profile.computeValueHashes().get(0)).hasSize(4);
        }

        @Test
        @DisplayName("hashes should be stable")
        void hashesShouldBeStable() {
            var profile = CovariateProfile.builder()
                .put("weekday_vs_weekend", "Mo-Fr")
                .build();
            
            var hashes1 = profile.computeValueHashes();
            var hashes2 = profile.computeValueHashes();
            
            assertThat(hashes1).isEqualTo(hashes2);
        }
    }

    @Nested
    @DisplayName("UNDEFINED constant")
    class UndefinedTests {

        @Test
        @DisplayName("UNDEFINED should be 'UNDEFINED'")
        void undefinedShouldBeUppercase() {
            assertThat(CovariateProfile.UNDEFINED).isEqualTo("UNDEFINED");
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("equal profiles should be equal")
        void equalProfilesShouldBeEqual() {
            var profile1 = CovariateProfile.builder()
                .put("a", "1")
                .put("b", "2")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("a", "1")
                .put("b", "2")
                .build();
            
            assertThat(profile1).isEqualTo(profile2);
            assertThat(profile1.hashCode()).isEqualTo(profile2.hashCode());
        }

        @Test
        @DisplayName("profiles with different values should not be equal")
        void profilesWithDifferentValuesShouldNotBeEqual() {
            var profile1 = CovariateProfile.builder()
                .put("a", "1")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("a", "2")
                .build();
            
            assertThat(profile1).isNotEqualTo(profile2);
        }

        @Test
        @DisplayName("profiles with different ordering should not be equal")
        void profilesWithDifferentOrderingShouldNotBeEqual() {
            var profile1 = CovariateProfile.builder()
                .put("a", "1")
                .put("b", "2")
                .build();
            var profile2 = CovariateProfile.builder()
                .put("b", "2")
                .put("a", "1")
                .build();
            
            assertThat(profile1).isNotEqualTo(profile2);
        }
    }

    @Nested
    @DisplayName("TimeWindowValue support")
    class TimeWindowValueSupportTests {

        @Test
        @DisplayName("should handle TimeWindowValue correctly")
        void shouldHandleTimeWindowValueCorrectly() {
            var timeWindow = new CovariateValue.TimeWindowValue(
                LocalTime.of(14, 30),
                LocalTime.of(14, 45),
                ZoneId.of("Europe/London")
            );
            
            var profile = CovariateProfile.builder()
                .put("time_of_day", timeWindow)
                .build();
            
            assertThat(profile.get("time_of_day")).isEqualTo(timeWindow);
            assertThat(profile.computeHash()).isNotEmpty();
        }
    }
}

