package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.javai.punit.api.Input;
import org.javai.punit.api.InputSource;
import org.javai.punit.api.OutcomeCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

@DisplayName("@Input annotation validation")
class InputAnnotationValidationTest {

    @Nested
    @DisplayName("valid configurations")
    class ValidConfigurations {

        @Test
        @DisplayName("@Input with @InputSource is accepted")
        void inputWithInputSourceIsAccepted() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withInputAndInputSource", OutcomeCaptor.class, String.class);

            assertThatCode(() -> ExperimentExtension.validateInputAnnotation(method))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("no @Input and no @InputSource is accepted")
        void noInputAnnotationIsAccepted() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withoutInputAnnotation", OutcomeCaptor.class, String.class);

            assertThatCode(() -> ExperimentExtension.validateInputAnnotation(method))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("invalid configurations")
    class InvalidConfigurations {

        @Test
        @DisplayName("@Input without @InputSource is rejected")
        void inputWithoutInputSourceIsRejected() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withInputButNoInputSource", OutcomeCaptor.class, String.class);

            assertThatThrownBy(() -> ExperimentExtension.validateInputAnnotation(method))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("@Input")
                    .hasMessageContaining("@InputSource")
                    .hasMessageContaining("withInputButNoInputSource");
        }
    }

    @SuppressWarnings("unused")
    static class TestMethods {

        @InputSource("someSource")
        void withInputAndInputSource(OutcomeCaptor captor, @Input String input) {
        }

        void withoutInputAnnotation(OutcomeCaptor captor, String input) {
        }

        void withInputButNoInputSource(OutcomeCaptor captor, @Input String input) {
        }
    }
}
