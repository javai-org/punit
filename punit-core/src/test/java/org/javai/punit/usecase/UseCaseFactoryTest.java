package org.javai.punit.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.javai.punit.api.FactorSetter;
import org.javai.punit.api.FactorValues;
import org.javai.punit.api.UseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseFactory")
class UseCaseFactoryTest {

    private UseCaseFactory factory;

    @BeforeEach
    void setUp() {
        factory = new UseCaseFactory();
    }

    // Test use case classes

    public static class SimpleUseCase {
        private final String value;
        public SimpleUseCase(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    @UseCase("custom-id")
    public static class AnnotatedUseCase {}

    @UseCase
    public static class DefaultAnnotatedUseCase {}

    public static class FactorAwareUseCase {
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
        void createsNewInstanceEachTime() {
            factory.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));

            SimpleUseCase first = factory.getInstance(SimpleUseCase.class);
            SimpleUseCase second = factory.getInstance(SimpleUseCase.class);

            assertThat(first).isNotSameAs(second);
            assertThat(first.getValue()).isEqualTo("test");
        }

        @Test
        @DisplayName("supports fluent chaining of register calls")
        void supportsFluentChaining() {
            factory.register(SimpleUseCase.class, () -> new SimpleUseCase("a"))
                   .register(AnnotatedUseCase.class, AnnotatedUseCase::new);

            assertThat(factory.isRegistered(SimpleUseCase.class)).isTrue();
            assertThat(factory.isRegistered(AnnotatedUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("throws when no factory registered")
        void throwsWhenNotRegistered() {
            assertThatThrownBy(() -> factory.getInstance(SimpleUseCase.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No factory registered");
        }
    }

    @Nested
    @DisplayName("singleton mode")
    class SingletonMode {

        @Test
        @DisplayName("returns same instance in singleton mode")
        void returnsSameInstanceInSingletonMode() {
            var singletonFactory = new UseCaseFactory(true);
            singletonFactory.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));

            SimpleUseCase first = singletonFactory.getInstance(SimpleUseCase.class);
            SimpleUseCase second = singletonFactory.getInstance(SimpleUseCase.class);

            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("calls factory only once in singleton mode")
        void callsFactoryOnlyOnce() {
            var singletonFactory = new UseCaseFactory(true);
            var counter = new AtomicInteger(0);
            singletonFactory.register(SimpleUseCase.class, () -> {
                counter.incrementAndGet();
                return new SimpleUseCase("test");
            });

            singletonFactory.getInstance(SimpleUseCase.class);
            singletonFactory.getInstance(SimpleUseCase.class);

            assertThat(counter.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("registerWithFactors")
    class RegisterWithFactors {

        @Test
        @DisplayName("uses factor factory when factor values are set")
        void usesFactorFactory() {
            factory.registerWithFactors(SimpleUseCase.class,
                factors -> new SimpleUseCase(factors.getString("name")));
            factory.setCurrentFactorValues(new Object[]{"gpt-4"}, List.of("name"));

            SimpleUseCase result = factory.getInstance(SimpleUseCase.class);

            assertThat(result.getValue()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("throws when factor factory registered but no factor values")
        void throwsWithoutFactorValues() {
            factory.registerWithFactors(SimpleUseCase.class,
                factors -> new SimpleUseCase("test"));

            assertThatThrownBy(() -> factory.getInstance(SimpleUseCase.class))
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
            factory.registerAutoWired(FactorAwareUseCase.class, FactorAwareUseCase::new);
            factory.setCurrentFactorValues(
                new Object[]{"gpt-4", 0.7},
                List.of("model", "temp")
            );

            FactorAwareUseCase useCase = factory.getInstance(FactorAwareUseCase.class);

            assertThat(useCase.getModel()).isEqualTo("gpt-4");
            assertThat(useCase.getTemperature()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("throws when factor setter references missing factor")
        void throwsWhenFactorMissing() {
            factory.registerAutoWired(FactorAwareUseCase.class, FactorAwareUseCase::new);
            factory.setCurrentFactorValues(
                new Object[]{"gpt-4"},
                List.of("model")
            );

            assertThatThrownBy(() -> factory.getInstance(FactorAwareUseCase.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no such factor exists");
        }
    }

    @Nested
    @DisplayName("factor values management")
    class FactorValuesManagement {

        @Test
        @DisplayName("setCurrentFactorValues stores values")
        void setsFactorValues() {
            factory.setCurrentFactorValues(new Object[]{"a"}, List.of("x"));
            assertThat(factory.getCurrentFactorValues()).isNotNull();
        }

        @Test
        @DisplayName("clearCurrentFactorValues clears values")
        void clearsFactorValues() {
            factory.setCurrentFactorValues(new Object[]{"a"}, List.of("x"));
            factory.clearCurrentFactorValues();
            assertThat(factory.getCurrentFactorValues()).isNull();
        }

        @Test
        @DisplayName("setCurrentFactorValues with FactorValues object")
        void setsFactorValuesWithObject() {
            FactorValues values = new FactorValues(new Object[]{"test"}, List.of("name"));
            factory.setCurrentFactorValues(values);
            assertThat(factory.getCurrentFactorValues()).isSameAs(values);
        }
    }

    @Nested
    @DisplayName("registration checks")
    class RegistrationChecks {

        @Test
        @DisplayName("isRegistered returns true for regular factory")
        void isRegisteredForRegularFactory() {
            factory.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));
            assertThat(factory.isRegistered(SimpleUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("isRegistered returns true for factor factory")
        void isRegisteredForFactorFactory() {
            factory.registerWithFactors(SimpleUseCase.class, f -> new SimpleUseCase("test"));
            assertThat(factory.isRegistered(SimpleUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("isRegistered returns false for unregistered class")
        void isNotRegisteredForUnknown() {
            assertThat(factory.isRegistered(SimpleUseCase.class)).isFalse();
        }

        @Test
        @DisplayName("hasFactorFactory returns true for factor-aware factory")
        void hasFactorFactoryForFactorAware() {
            factory.registerWithFactors(SimpleUseCase.class, f -> new SimpleUseCase("test"));
            assertThat(factory.hasFactorFactory(SimpleUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("hasFactorFactory returns true for auto-wired factory")
        void hasFactorFactoryForAutoWired() {
            factory.registerAutoWired(FactorAwareUseCase.class, FactorAwareUseCase::new);
            assertThat(factory.hasFactorFactory(FactorAwareUseCase.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("resolveId")
    class ResolveId {

        @Test
        @DisplayName("uses @UseCase value when present")
        void usesAnnotationValue() {
            assertThat(UseCaseFactory.resolveId(AnnotatedUseCase.class)).isEqualTo("custom-id");
        }

        @Test
        @DisplayName("uses simple class name when @UseCase has empty value")
        void usesClassNameForEmptyAnnotation() {
            assertThat(UseCaseFactory.resolveId(DefaultAnnotatedUseCase.class)).isEqualTo("DefaultAnnotatedUseCase");
        }

        @Test
        @DisplayName("uses simple class name when no annotation")
        void usesClassNameForNoAnnotation() {
            assertThat(UseCaseFactory.resolveId(SimpleUseCase.class)).isEqualTo("SimpleUseCase");
        }
    }

    @Nested
    @DisplayName("getCurrentInstance")
    class GetCurrentInstance {

        @Test
        @DisplayName("returns last created instance")
        void returnsLastCreated() {
            factory.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));
            SimpleUseCase instance = factory.getInstance(SimpleUseCase.class);

            assertThat(factory.getCurrentInstance(SimpleUseCase.class)).isSameAs(instance);
        }

        @Test
        @DisplayName("returns null when no instance created")
        void returnsNullWhenNoneCreated() {
            assertThat(factory.getCurrentInstance(SimpleUseCase.class)).isNull();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("clears all factories and state")
        void clearsAll() {
            factory.register(SimpleUseCase.class, () -> new SimpleUseCase("test"));
            factory.setCurrentFactorValues(new Object[]{"a"}, List.of("x"));
            factory.getInstance(SimpleUseCase.class);

            factory.clear();

            assertThat(factory.isRegistered(SimpleUseCase.class)).isFalse();
            assertThat(factory.getCurrentFactorValues()).isNull();
            assertThat(factory.getCurrentInstance(SimpleUseCase.class)).isNull();
        }
    }

    @Nested
    @DisplayName("value conversion")
    class ValueConversion {

        public static class TypeConversionUseCase {
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
            factory.registerAutoWired(TypeConversionUseCase.class, TypeConversionUseCase::new);
            factory.setCurrentFactorValues(
                new Object[]{42.5, 100L, true, "test"},
                List.of("intVal", "longVal", "boolVal", "strVal")
            );

            TypeConversionUseCase useCase = factory.getInstance(TypeConversionUseCase.class);

            assertThat(useCase.intValue).isEqualTo(42);
            assertThat(useCase.longValue).isEqualTo(100L);
            assertThat(useCase.boolValue).isTrue();
            assertThat(useCase.stringValue).isEqualTo("test");
        }

        @Test
        @DisplayName("converts String to numeric types")
        void convertsStringToNumeric() {
            factory.registerAutoWired(TypeConversionUseCase.class, TypeConversionUseCase::new);
            factory.setCurrentFactorValues(
                new Object[]{"42", "100", "true", "hello"},
                List.of("intVal", "longVal", "boolVal", "strVal")
            );

            TypeConversionUseCase useCase = factory.getInstance(TypeConversionUseCase.class);

            assertThat(useCase.intValue).isEqualTo(42);
            assertThat(useCase.longValue).isEqualTo(100L);
            assertThat(useCase.boolValue).isTrue();
        }
    }
}
