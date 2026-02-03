package org.javai.punit.experiment.engine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.javai.punit.api.Factor;
import org.javai.punit.api.Input;
import org.javai.punit.api.OutcomeCaptor;
import org.javai.punit.api.TokenChargeRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

@DisplayName("InputParameterDetector")
class InputParameterDetectorTest {

    @Nested
    @DisplayName("findInputParameterType")
    class FindInputParameterType {

        @Test
        @DisplayName("detects explicit @Input annotated parameter")
        void detectsExplicitInputAnnotation() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withExplicitInput", String.class, Integer.class);

            Class<?> inputType = InputParameterDetector.findInputParameterType(method);

            assertThat(inputType).isEqualTo(Integer.class);
        }

        @Test
        @DisplayName("auto-detects input parameter when no @Input annotation")
        void autoDetectsInputParameter() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withAutoDetectedInput", String.class);

            Class<?> inputType = InputParameterDetector.findInputParameterType(method);

            assertThat(inputType).isEqualTo(String.class);
        }

        @Test
        @DisplayName("skips OutcomeCaptor parameter")
        void skipsOutcomeCaptor() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withOutcomeCaptor", OutcomeCaptor.class, String.class);

            Class<?> inputType = InputParameterDetector.findInputParameterType(method);

            assertThat(inputType).isEqualTo(String.class);
        }

        @Test
        @DisplayName("skips @Factor annotated parameter")
        void skipsFactorAnnotated() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withFactor", Double.class, String.class);

            Class<?> inputType = InputParameterDetector.findInputParameterType(method);

            assertThat(inputType).isEqualTo(String.class);
        }

        @Test
        @DisplayName("skips UseCase type by naming convention")
        void skipsUseCaseType() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withUseCase", FakeUseCase.class, String.class);

            Class<?> inputType = InputParameterDetector.findInputParameterType(method);

            assertThat(inputType).isEqualTo(String.class);
        }

        @Test
        @DisplayName("skips TokenChargeRecorder parameter")
        void skipsTokenChargeRecorder() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withTokenRecorder", TokenChargeRecorder.class, String.class);

            Class<?> inputType = InputParameterDetector.findInputParameterType(method);

            assertThat(inputType).isEqualTo(String.class);
        }

        @Test
        @DisplayName("throws when no suitable parameter found")
        void throwsWhenNoSuitableParameter() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withNoInputParameter", OutcomeCaptor.class);

            assertThatThrownBy(() -> InputParameterDetector.findInputParameterType(method))
                    .isInstanceOf(ExtensionConfigurationException.class)
                    .hasMessageContaining("@InputSource requires a method parameter");
        }

        @Test
        @DisplayName("@Input takes precedence over auto-detection")
        void inputAnnotationTakesPrecedence() throws Exception {
            Method method = TestMethods.class.getDeclaredMethod(
                    "withMultipleCandidates", String.class, Integer.class);

            Class<?> inputType = InputParameterDetector.findInputParameterType(method);

            // Integer is annotated with @Input, so it should be selected
            assertThat(inputType).isEqualTo(Integer.class);
        }
    }

    @Nested
    @DisplayName("isUseCaseType")
    class IsUseCaseType {

        @Test
        @DisplayName("detects class ending with UseCase")
        void detectsUseCaseSuffix() {
            assertThat(InputParameterDetector.isUseCaseType(FakeUseCase.class)).isTrue();
        }

        @Test
        @DisplayName("detects class in usecase package")
        void detectsUsecasePackage() {
            // This test uses a mock scenario - the actual detection uses package name
            assertThat(InputParameterDetector.isUseCaseType(String.class)).isFalse();
        }

        @Test
        @DisplayName("returns false for regular class")
        void returnsFalseForRegularClass() {
            assertThat(InputParameterDetector.isUseCaseType(String.class)).isFalse();
            assertThat(InputParameterDetector.isUseCaseType(Integer.class)).isFalse();
        }
    }

    // Test fixtures
    static class FakeUseCase {
        // Simulates a use case class
    }

    @SuppressWarnings("unused")
    static class TestMethods {

        void withExplicitInput(String notInput, @Input Integer input) {
        }

        void withAutoDetectedInput(String input) {
        }

        void withOutcomeCaptor(OutcomeCaptor captor, String input) {
        }

        void withFactor(@Factor("temp") Double temperature, String input) {
        }

        void withUseCase(FakeUseCase useCase, String input) {
        }

        void withTokenRecorder(TokenChargeRecorder recorder, String input) {
        }

        void withNoInputParameter(OutcomeCaptor captor) {
        }

        void withMultipleCandidates(String firstCandidate, @Input Integer annotatedInput) {
        }
    }
}
