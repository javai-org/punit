package org.javai.punit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseProvider")
class UseCaseProviderTest {

    private UseCaseProvider provider;

    @BeforeEach
    void setUp() {
        provider = new UseCaseProvider();
    }

    // Test use case classes
    static class SimpleUseCase {
        private final String value;
        SimpleUseCase(String value) { this.value = value; }
        String getValue() { return value; }
    }

    @UseCase("custom-id")
    static class AnnotatedUseCase {}

    @UseCase
    static class DefaultAnnotatedUseCase {}

    static class FactorAwareUseCase {
        private String model;
        private double temperature;
        
        @FactorSetter("model")
        public void setModel(String model) { this.model = model; }
        
        @FactorSetter("temp")
        public void setTemperature(double temp) { this.temperature = temp; }
        
        public String getModel() { return model; }
        public double getTemperature() { return temperature; }
    }

    @Nested
    @DisplayName("register and getInstance")
    class RegisterAndGetInstance {

        @Test
        @DisplayName("creates new instance on each call by default")
        void createsNewInstanceOnEachCall() {
            provider.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));

            SimpleUseCase first = provider.getInstance(SimpleUseCase.class);
            SimpleUseCase second = provider.getInstance(SimpleUseCase.class);

            assertThat(first).isNotSameAs(second);
        }

        @Test
        @DisplayName("throws when no factory registered")
        void throwsWhenNoFactoryRegistered() {
            assertThatThrownBy(() -> provider.getInstance(SimpleUseCase.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No factory registered");
        }

        @Test
        @DisplayName("supports fluent chaining")
        void supportsFluentChaining() {
            UseCaseProvider result = provider
                .register(SimpleUseCase.class, () -> new SimpleUseCase("a"));

            assertThat(result).isSameAs(provider);
        }

        @Test
        @DisplayName("clears singleton cache on re-register")
        void clearsSingletonCacheOnReRegister() {
            var singletonProvider = new UseCaseProvider(true);
            singletonProvider.register(SimpleUseCase.class, () -> new SimpleUseCase("first"));
            var first = singletonProvider.getInstance(SimpleUseCase.class);
            
            singletonProvider.register(SimpleUseCase.class, () -> new SimpleUseCase("second"));
            var second = singletonProvider.getInstance(SimpleUseCase.class);

            assertThat(first.getValue()).isEqualTo("first");
            assertThat(second.getValue()).isEqualTo("second");
        }
    }

    @Nested
    @DisplayName("singleton mode")
    class SingletonMode {

        @Test
        @DisplayName("returns same instance in singleton mode")
        void returnsSameInstanceInSingletonMode() {
            var singletonProvider = new UseCaseProvider(true);
            singletonProvider.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));

            SimpleUseCase first = singletonProvider.getInstance(SimpleUseCase.class);
            SimpleUseCase second = singletonProvider.getInstance(SimpleUseCase.class);

            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("calls factory only once in singleton mode")
        void callsFactoryOnlyOnce() {
            var singletonProvider = new UseCaseProvider(true);
            AtomicInteger callCount = new AtomicInteger();
            singletonProvider.register(SimpleUseCase.class, () -> {
                callCount.incrementAndGet();
                return new SimpleUseCase("test");
            });

            singletonProvider.getInstance(SimpleUseCase.class);
            singletonProvider.getInstance(SimpleUseCase.class);
            singletonProvider.getInstance(SimpleUseCase.class);

            assertThat(callCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("registerWithFactors")
    class RegisterWithFactors {

        @Test
        @DisplayName("uses factor factory when factor values are set")
        void usesFactorFactoryWhenFactorValuesSet() {
            provider.registerWithFactors(SimpleUseCase.class, factors -> 
                new SimpleUseCase(factors.getString("name")));
            provider.setCurrentFactorValues(new Object[]{"TestName"}, List.of("name"));

            SimpleUseCase useCase = provider.getInstance(SimpleUseCase.class);

            assertThat(useCase.getValue()).isEqualTo("TestName");
        }

        @Test
        @DisplayName("throws when factor factory registered but no factor values")
        void throwsWhenNoFactorValues() {
            provider.registerWithFactors(SimpleUseCase.class, factors -> 
                new SimpleUseCase(factors.getString("name")));

            assertThatThrownBy(() -> provider.getInstance(SimpleUseCase.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no factor values set");
        }
    }

    @Nested
    @DisplayName("registerAutoWired")
    class RegisterAutoWired {

        @Test
        @DisplayName("injects factor values via @FactorSetter methods")
        void injectsFactorValues() {
            provider.registerAutoWired(FactorAwareUseCase.class, FactorAwareUseCase::new);
            provider.setCurrentFactorValues(
                new Object[]{"gpt-4", 0.7}, 
                List.of("model", "temp")
            );

            FactorAwareUseCase useCase = provider.getInstance(FactorAwareUseCase.class);

            assertThat(useCase.getModel()).isEqualTo("gpt-4");
            assertThat(useCase.getTemperature()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("throws when factor setter references missing factor")
        void throwsWhenFactorMissing() {
            provider.registerAutoWired(FactorAwareUseCase.class, FactorAwareUseCase::new);
            provider.setCurrentFactorValues(
                new Object[]{"gpt-4"}, 
                List.of("model")  // Missing "temp"
            );

            assertThatThrownBy(() -> provider.getInstance(FactorAwareUseCase.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no such factor exists");
        }
    }

    @Nested
    @DisplayName("factor values management")
    class FactorValuesManagement {

        @Test
        @DisplayName("setCurrentFactorValues stores values")
        void setCurrentFactorValuesStoresValues() {
            provider.setCurrentFactorValues(new Object[]{"a", "b"}, List.of("x", "y"));

            assertThat(provider.getCurrentFactorValues()).isNotNull();
            assertThat(provider.getCurrentFactorValues().get("x")).isEqualTo("a");
        }

        @Test
        @DisplayName("clearCurrentFactorValues clears values")
        void clearCurrentFactorValuesClearsValues() {
            provider.setCurrentFactorValues(new Object[]{"a"}, List.of("x"));
            provider.clearCurrentFactorValues();

            assertThat(provider.getCurrentFactorValues()).isNull();
        }

        @Test
        @DisplayName("setCurrentFactorValues with FactorValues object")
        void setCurrentFactorValuesWithObject() {
            FactorValues values = new FactorValues(new Object[]{"test"}, List.of("name"));
            provider.setCurrentFactorValues(values);

            assertThat(provider.getCurrentFactorValues()).isSameAs(values);
        }
    }

    @Nested
    @DisplayName("isRegistered")
    class IsRegistered {

        @Test
        @DisplayName("returns true for registered factory")
        void returnsTrueForRegisteredFactory() {
            provider.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));

            assertThat(provider.isRegistered(SimpleUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("returns true for factor factory")
        void returnsTrueForFactorFactory() {
            provider.registerWithFactors(SimpleUseCase.class, f -> new SimpleUseCase("test"));

            assertThat(provider.isRegistered(SimpleUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("returns true for auto-wired factory")
        void returnsTrueForAutoWiredFactory() {
            provider.registerAutoWired(FactorAwareUseCase.class, FactorAwareUseCase::new);

            assertThat(provider.isRegistered(FactorAwareUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("returns false for unregistered class")
        void returnsFalseForUnregisteredClass() {
            assertThat(provider.isRegistered(SimpleUseCase.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasFactorFactory")
    class HasFactorFactory {

        @Test
        @DisplayName("returns true for factor factory")
        void returnsTrueForFactorFactory() {
            provider.registerWithFactors(SimpleUseCase.class, f -> new SimpleUseCase("test"));

            assertThat(provider.hasFactorFactory(SimpleUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("returns true for auto-wired factory")
        void returnsTrueForAutoWiredFactory() {
            provider.registerAutoWired(FactorAwareUseCase.class, FactorAwareUseCase::new);

            assertThat(provider.hasFactorFactory(FactorAwareUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("returns false for regular factory")
        void returnsFalseForRegularFactory() {
            provider.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));

            assertThat(provider.hasFactorFactory(SimpleUseCase.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("clears all factories")
        void clearsAllFactories() {
            provider.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));
            provider.registerWithFactors(AnnotatedUseCase.class, f -> new AnnotatedUseCase());

            provider.clear();

            assertThat(provider.isRegistered(SimpleUseCase.class)).isFalse();
            assertThat(provider.isRegistered(AnnotatedUseCase.class)).isFalse();
        }

        @Test
        @DisplayName("clears factor values")
        void clearsFactorValues() {
            provider.setCurrentFactorValues(new Object[]{"a"}, List.of("x"));

            provider.clear();

            assertThat(provider.getCurrentFactorValues()).isNull();
        }
    }

    @Nested
    @DisplayName("resolveId")
    class ResolveId {

        @Test
        @DisplayName("uses @UseCase value when present")
        void usesUseCaseValueWhenPresent() {
            String id = UseCaseProvider.resolveId(AnnotatedUseCase.class);

            assertThat(id).isEqualTo("custom-id");
        }

        @Test
        @DisplayName("uses simple class name when @UseCase has empty value")
        void usesSimpleClassNameWhenEmpty() {
            String id = UseCaseProvider.resolveId(DefaultAnnotatedUseCase.class);

            assertThat(id).isEqualTo("DefaultAnnotatedUseCase");
        }

        @Test
        @DisplayName("uses simple class name when no annotation")
        void usesSimpleClassNameWhenNoAnnotation() {
            String id = UseCaseProvider.resolveId(SimpleUseCase.class);

            assertThat(id).isEqualTo("SimpleUseCase");
        }
    }

    @Nested
    @DisplayName("value conversion")
    class ValueConversion {

        static class TypeConversionUseCase {
            private int intValue;
            private long longValue;
            private boolean boolValue;
            private String stringValue;

            @FactorSetter("intVal")
            public void setIntValue(int val) { this.intValue = val; }

            @FactorSetter("longVal")
            public void setLongValue(long val) { this.longValue = val; }

            @FactorSetter("boolVal")
            public void setBoolValue(boolean val) { this.boolValue = val; }

            @FactorSetter("strVal")
            public void setStringValue(String val) { this.stringValue = val; }
        }

        @Test
        @DisplayName("converts Number to int")
        void convertsNumberToInt() {
            provider.registerAutoWired(TypeConversionUseCase.class, TypeConversionUseCase::new);
            provider.setCurrentFactorValues(
                new Object[]{42.5, 100L, true, "test"}, 
                List.of("intVal", "longVal", "boolVal", "strVal")
            );

            TypeConversionUseCase useCase = provider.getInstance(TypeConversionUseCase.class);

            assertThat(useCase.intValue).isEqualTo(42);
            assertThat(useCase.longValue).isEqualTo(100L);
            assertThat(useCase.boolValue).isTrue();
            assertThat(useCase.stringValue).isEqualTo("test");
        }

        @Test
        @DisplayName("converts String to numeric types")
        void convertsStringToNumeric() {
            provider.registerAutoWired(TypeConversionUseCase.class, TypeConversionUseCase::new);
            provider.setCurrentFactorValues(
                new Object[]{"42", "100", "true", "hello"}, 
                List.of("intVal", "longVal", "boolVal", "strVal")
            );

            TypeConversionUseCase useCase = provider.getInstance(TypeConversionUseCase.class);

            assertThat(useCase.intValue).isEqualTo(42);
            assertThat(useCase.longValue).isEqualTo(100L);
            assertThat(useCase.boolValue).isTrue();
        }
    }
}

