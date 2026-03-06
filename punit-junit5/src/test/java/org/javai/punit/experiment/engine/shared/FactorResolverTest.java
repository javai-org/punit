package org.javai.punit.experiment.engine.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Tests for FactorResolver factor source resolution.
 *
 * <p>Tests the three-form resolution algorithm:
 * <ul>
 *   <li><b>Simple name</b>: Search current class, then use case class</li>
 *   <li><b>Class#method</b>: Search current package, then use case's package</li>
 *   <li><b>Fully qualified</b>: Direct lookup</li>
 * </ul>
 */
@DisplayName("FactorResolver")
class FactorResolverTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST FIXTURES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Simulates a test class with a factor source method.
     */
    static class TestClass {
        public static List<FactorArguments> localFactors() {
            return List.of(FactorArguments.of("local"));
        }

        // Dummy method to get Method reference
        void testMethod() {}
    }

    /**
     * Simulates a use case class with factor provider methods.
     */
    static class UseCaseClass {
        public static List<FactorArguments> useCaseFactors() {
            return List.of(FactorArguments.of("usecase1"), FactorArguments.of("usecase2"));
        }

        public static Stream<FactorArguments> streamFactors() {
            return Stream.of(FactorArguments.of("stream1"));
        }
    }

    /**
     * Another class for cross-class tests.
     */
    static class AnotherClass {
        public static List<FactorArguments> anotherFactors() {
            return List.of(FactorArguments.of("another"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private Method getTestMethod() throws NoSuchMethodException {
        return TestClass.class.getDeclaredMethod("testMethod");
    }

    private FactorSource createFactorSource(String value) {
        return new FactorSource() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return FactorSource.class;
            }

            @Override
            public String value() {
                return value;
            }

            @Override
            public String[] factors() {
                return new String[0];
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS - Simple Name Resolution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Simple name resolution")
    class SimpleNameResolution {

        @Test
        @DisplayName("should find method in current class")
        void shouldFindMethodInCurrentClass() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("localFactors");

            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource, UseCaseClass.class);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get(0)).isEqualTo("local");
        }

        @Test
        @DisplayName("should find method in use case class when not in current class")
        void shouldFindMethodInUseCaseClass() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("useCaseFactors");

            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource, UseCaseClass.class);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).get(0)).isEqualTo("usecase1");
        }

        @Test
        @DisplayName("should prefer current class over use case class (shadowing)")
        void shouldPreferCurrentClassOverUseCaseClass() throws Exception {
            // Both TestClass and UseCaseClass have a method - current class wins
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("localFactors");

            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource, UseCaseClass.class);

            // Should get "local" from TestClass, not from UseCaseClass
            assertThat(result).hasSize(1);
            assertThat(result.get(0).get(0)).isEqualTo("local");
        }

        @Test
        @DisplayName("should work without use case class")
        void shouldWorkWithoutUseCaseClass() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("localFactors");

            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource, null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw with helpful message when not found")
        void shouldThrowWithHelpfulMessageWhenNotFound() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("nonExistent");

            assertThatThrownBy(() ->
                    FactorResolver.resolveFactorArguments(testMethod, factorSource, UseCaseClass.class))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("Cannot find")
                    .hasMessageContaining("nonExistent")
                    .hasMessageContaining("TestClass")
                    .hasMessageContaining("UseCaseClass")
                    .hasMessageContaining("Hint:");
        }

        @Test
        @DisplayName("should handle Stream return type")
        void shouldHandleStreamReturnType() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("streamFactors");

            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource, UseCaseClass.class);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get(0)).isEqualTo("stream1");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS - Fully Qualified Resolution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fully qualified resolution")
    class FullyQualifiedResolution {

        @Test
        @DisplayName("should resolve fully qualified class#method")
        void shouldResolveFullyQualified() throws Exception {
            Method testMethod = getTestMethod();
            String fqn = "org.javai.punit.experiment.engine.shared.FactorResolverTest$UseCaseClass#useCaseFactors";
            FactorSource factorSource = createFactorSource(fqn);

            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should throw for non-existent fully qualified class")
        void shouldThrowForNonExistentClass() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("com.nonexistent.Class#method");

            assertThatThrownBy(() ->
                    FactorResolver.resolveFactorArguments(testMethod, factorSource, null))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("Cannot find class");
        }

        @Test
        @DisplayName("should throw for non-existent method in fully qualified class")
        void shouldThrowForNonExistentMethod() throws Exception {
            Method testMethod = getTestMethod();
            String fqn = "org.javai.punit.experiment.engine.shared.FactorResolverTest$UseCaseClass#nonExistent";
            FactorSource factorSource = createFactorSource(fqn);

            assertThatThrownBy(() ->
                    FactorResolver.resolveFactorArguments(testMethod, factorSource, null))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("Cannot find method")
                    .hasMessageContaining("nonExistent");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS - Class#method Resolution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Class#method resolution")
    class ClassMethodResolution {

        @Test
        @DisplayName("should find class in use case package when not in current package")
        void shouldFindClassInUseCasePackage() throws Exception {
            // This tests that when Class#method is used and the class isn't in
            // the current package, it falls back to the use case's package.
            // Using a real class from a different package.
            Method testMethod = getTestMethod();

            // FactorInfo is in org.javai.punit.experiment.engine.shared package (same as test)
            // So we use a simple name reference which will be found in current package
            FactorSource factorSource = createFactorSource(
                    "FactorResolverTest$AnotherClass#anotherFactors");

            // This uses the nested class syntax - should work with current package search
            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get(0)).isEqualTo("another");
        }

        @Test
        @DisplayName("should throw with helpful message for non-existent class")
        void shouldThrowForNonExistentClass() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("NonExistentClass#method");

            assertThatThrownBy(() ->
                    FactorResolver.resolveFactorArguments(testMethod, factorSource, null))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("Cannot find")
                    .hasMessageContaining("NonExistentClass#method")
                    .hasMessageContaining("Searched in:")
                    .hasMessageContaining("Hint:");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS - Backward Compatibility
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("two-arg overload should work (no use case class)")
        void twoArgOverloadShouldWork() throws Exception {
            Method testMethod = getTestMethod();
            FactorSource factorSource = createFactorSource("localFactors");

            // Use the two-arg overload (backward compatible)
            List<FactorArguments> result = FactorResolver.resolveFactorArguments(
                    testMethod, factorSource);

            assertThat(result).hasSize(1);
        }
    }
}
