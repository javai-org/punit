package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.HashableFactorSource;
import org.javai.punit.experiment.engine.FactorSourceAdapter.FactorSourceResolutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for FactorSourceAdapter.
 * 
 * <p>Factor sources are resolved by method name - no annotation is required
 * on the provider method. The method must be static, accept no parameters,
 * and return either {@code List<FactorArguments>} or {@code Stream<FactorArguments>}.
 */
@DisplayName("FactorSourceAdapter")
class FactorSourceAdapterTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST FIXTURES - Classes with factor source methods
    // ═══════════════════════════════════════════════════════════════════════════

    static class ValidFactorSources {
        public static Stream<FactorArguments> streamSource() {
            return Stream.of(
                    FactorArguments.of("a", 1),
                    FactorArguments.of("b", 2)
            );
        }

        public static List<FactorArguments> listSource() {
            return List.of(
                    FactorArguments.of("x"),
                    FactorArguments.of("y"),
                    FactorArguments.of("z")
            );
        }

        public static List<FactorArguments> anotherListSource() {
            return List.of(FactorArguments.of("another"));
        }
    }

    static class InvalidFactorSources {
        // Non-static method - should be rejected
        public List<FactorArguments> nonStatic() {
            return List.of();
        }

        // Method with parameters - should be rejected
        public static List<FactorArguments> withParameters(String param) {
            return List.of();
        }

        // Wrong return type - should be rejected
        public static String wrongReturnType() {
            return "not a factor source";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("fromReference - same class")
    class FromReferenceSameClass {

        @Test
        @DisplayName("should resolve Stream-returning method by name")
        void shouldResolveStreamMethod() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "streamSource", ValidFactorSources.class);

            assertThat(source.getSourceName()).isEqualTo("streamSource");
            assertThat(source.factors().toList()).hasSize(2);
        }

        @Test
        @DisplayName("should resolve List-returning method by name")
        void shouldResolveListMethod() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "listSource", ValidFactorSources.class);

            assertThat(source.getSourceName()).isEqualTo("listSource");
            assertThat(source.factors().toList()).hasSize(3);
        }

        @Test
        @DisplayName("should throw for non-existent method")
        void shouldThrowForNonExistentMethod() {
            assertThatThrownBy(() ->
                    FactorSourceAdapter.fromReference("nonExistent", ValidFactorSources.class))
                    .isInstanceOf(FactorSourceResolutionException.class)
                    .hasMessageContaining("Cannot find");
        }
    }

    @Nested
    @DisplayName("fromReference - cross-class")
    class FromReferenceCrossClass {

        @Test
        @DisplayName("should resolve cross-class reference with # separator")
        void shouldResolveCrossClassReference() {
            // Use fully qualified class name for nested class
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "org.javai.punit.experiment.engine.FactorSourceAdapterTest$ValidFactorSources#listSource",
                    FactorSourceAdapterTest.class);

            assertThat(source.factors().toList()).hasSize(3);
        }

        @Test
        @DisplayName("should throw for non-existent class")
        void shouldThrowForNonExistentClass() {
            assertThatThrownBy(() ->
                    FactorSourceAdapter.fromReference("NonExistentClass#method", ValidFactorSources.class))
                    .isInstanceOf(FactorSourceResolutionException.class)
                    .hasMessageContaining("Cannot find factor source");
        }
    }

    @Nested
    @DisplayName("Method validation")
    class MethodValidation {

        @Test
        @DisplayName("should reject non-static method")
        void shouldRejectNonStaticMethod() {
            assertThatThrownBy(() ->
                    FactorSourceAdapter.fromReference("nonStatic", InvalidFactorSources.class))
                    .isInstanceOf(FactorSourceResolutionException.class)
                    .hasMessageContaining("must be static");
        }

        @Test
        @DisplayName("should reject method with parameters")
        void shouldRejectMethodWithParameters() {
            assertThatThrownBy(() ->
                    FactorSourceAdapter.fromReference("withParameters", InvalidFactorSources.class))
                    .isInstanceOf(FactorSourceResolutionException.class)
                    .hasMessageContaining("Cannot find");  // Method takes params, so not found with no-arg signature
        }

        @Test
        @DisplayName("should reject method with wrong return type")
        void shouldRejectMethodWithWrongReturnType() {
            assertThatThrownBy(() ->
                    FactorSourceAdapter.fromReference("wrongReturnType", InvalidFactorSources.class))
                    .isInstanceOf(FactorSourceResolutionException.class)
                    .hasMessageContaining("must return");
        }
    }

    @Nested
    @DisplayName("Hash computation")
    class HashComputation {

        @Test
        @DisplayName("should compute hash for List source")
        void shouldComputeHashForListSource() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "listSource", ValidFactorSources.class);

            assertThat(source.getSourceHash())
                    .isNotNull()
                    .hasSize(64);  // SHA-256 hex
        }

        @Test
        @DisplayName("should compute hash for Stream source")
        void shouldComputeHashForStreamSource() {
            HashableFactorSource source = FactorSourceAdapter.fromReference(
                    "streamSource", ValidFactorSources.class);

            // Stream sources use path hash, not content hash
            assertThat(source.getSourceHash())
                    .isNotNull()
                    .isNotEmpty();
        }

        @Test
        @DisplayName("should produce same hash for multiple resolutions of same method")
        void shouldProduceSameHashForMultipleResolutions() {
            HashableFactorSource source1 = FactorSourceAdapter.fromReference(
                    "listSource", ValidFactorSources.class);
            HashableFactorSource source2 = FactorSourceAdapter.fromReference(
                    "listSource", ValidFactorSources.class);

            assertThat(source1.getSourceHash()).isEqualTo(source2.getSourceHash());
        }

        @Test
        @DisplayName("should produce different hash for different methods")
        void shouldProduceDifferentHashForDifferentMethods() {
            HashableFactorSource source1 = FactorSourceAdapter.fromReference(
                    "listSource", ValidFactorSources.class);
            HashableFactorSource source2 = FactorSourceAdapter.fromReference(
                    "anotherListSource", ValidFactorSources.class);

            assertThat(source1.getSourceHash()).isNotEqualTo(source2.getSourceHash());
        }
    }
}
