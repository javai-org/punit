package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.javai.punit.sentinel.testsubjects.FailingSentinel;
import org.javai.punit.sentinel.testsubjects.NoAnnotationClass;
import org.javai.punit.sentinel.testsubjects.PassingSentinel;
import org.javai.punit.usecase.UseCaseFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SentinelClassIntrospectorTest {

    private final SentinelClassIntrospector introspector = new SentinelClassIntrospector();

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("accepts @Sentinel-annotated class")
        void acceptsSentinelClass() {
            introspector.validate(PassingSentinel.class);
        }

        @Test
        @DisplayName("rejects class without @Sentinel")
        void rejectsNonSentinelClass() {
            assertThatThrownBy(() -> introspector.validate(NoAnnotationClass.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not annotated with @Sentinel");
        }
    }

    @Nested
    @DisplayName("instantiation")
    class Instantiation {

        @Test
        @DisplayName("instantiates sentinel class via no-arg constructor")
        void instantiatesSentinelClass() {
            Object instance = introspector.instantiate(PassingSentinel.class);
            assertThat(instance).isInstanceOf(PassingSentinel.class);
        }
    }

    @Nested
    @DisplayName("use case factory discovery")
    class UseCaseFactoryDiscovery {

        @Test
        @DisplayName("finds UseCaseFactory field on sentinel instance")
        void findsFactory() {
            Object instance = introspector.instantiate(PassingSentinel.class);
            UseCaseFactory factory = introspector.findUseCaseFactory(instance);
            assertThat(factory).isNotNull();
        }

        @Test
        @DisplayName("throws when no UseCaseFactory field exists")
        void throwsWhenNoFactory() {
            assertThatThrownBy(() -> introspector.findUseCaseFactory(new NoAnnotationClass()))
                    .isInstanceOf(SentinelExecutionException.class)
                    .hasMessageContaining("does not declare a UseCaseFactory field");
        }
    }

    @Nested
    @DisplayName("method discovery")
    class MethodDiscovery {

        @Test
        @DisplayName("finds @ProbabilisticTest methods")
        void findsTestMethods() {
            var methods = introspector.findTestMethods(PassingSentinel.class);
            assertThat(methods).hasSize(1);
            assertThat(methods.getFirst().getName()).isEqualTo("testStub");
        }

        @Test
        @DisplayName("returns empty list when no test methods exist")
        void returnsEmptyForNoTestMethods() {
            var methods = introspector.findTestMethods(NoAnnotationClass.class);
            assertThat(methods).isEmpty();
        }
    }

    @Nested
    @DisplayName("input resolution")
    class InputResolution {

        @Test
        @DisplayName("resolves @InputSource from static method")
        void resolvesInputSource() {
            var methods = introspector.findTestMethods(PassingSentinel.class);
            var inputs = introspector.resolveInputs(methods.getFirst(), PassingSentinel.class);
            assertThat(inputs).containsExactly("hello", "world", "foo");
        }
    }
}
