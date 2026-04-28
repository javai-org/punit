package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.javai.punit.api.legacy.MeasureExperiment;
import org.javai.punit.sentinel.testsubjects.ExperimentSentinel;
import org.javai.punit.usecase.UseCaseFactory;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SentinelExperimentExecutorTest {

    private SentinelExperimentExecutor executor;
    private SentinelClassIntrospector introspector;

    @BeforeEach
    void setUp() {
        introspector = new SentinelClassIntrospector();
        executor = new SentinelExperimentExecutor(
                new SentinelSampleExecutor(),
                introspector,
                new EnvironmentMetadata("test-env", "test-instance"));
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("returns PASS verdict for successful experiment")
        void passingExperiment(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                ProbabilisticTestVerdict verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.junitPassed()).isTrue();
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("formats experiment name using class and method")
        void formatsExperimentName(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                ProbabilisticTestVerdict verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.identity().className()).isEqualTo(ExperimentSentinel.class.getName());
                assertThat(verdict.identity().methodName()).isEqualTo("measureStub");
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("resolves use case ID from annotation")
        void resolvesUseCaseId(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                ProbabilisticTestVerdict verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.identity().useCaseId()).hasValue("stub-use-case");
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("includes environment metadata in verdict")
        void includesEnvironmentMetadata(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                ProbabilisticTestVerdict verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.environmentMetadata())
                        .containsEntry("environment", "test-env")
                        .containsEntry("instance", "test-instance");
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }
    }

    @Nested
    @DisplayName("spec file generation")
    class SpecFileGeneration {

        @Test
        @DisplayName("writes functional spec to output directory")
        void writesFunctionalSpec(@TempDir Path tempDir) throws IOException {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                executor.execute(method, annotation, sentinel, factory, ExperimentSentinel.class);

                Path specFile = tempDir.resolve("stub-use-case.yaml");
                assertThat(specFile).exists();
                String content = Files.readString(specFile);
                assertThat(content).contains("stub-use-case");
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("creates output directory if it does not exist")
        void createsOutputDirectory(@TempDir Path tempDir) {
            Path nestedDir = tempDir.resolve("nested").resolve("specs");
            System.setProperty("punit.spec.dir", nestedDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                ProbabilisticTestVerdict verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.junitPassed()).isTrue();
                assertThat(nestedDir).isDirectory();
                assertThat(nestedDir.resolve("stub-use-case.yaml")).exists();
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }
    }

    @Nested
    @DisplayName("execution summary")
    class ExecutionSummaryTests {

        @Test
        @DisplayName("contains sample counts in execution summary")
        void containsSampleCounts(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                ProbabilisticTestVerdict verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.execution().plannedSamples()).isEqualTo(5);
                assertThat(verdict.execution().samplesExecuted()).isGreaterThan(0);
                assertThat(verdict.execution().successes()).isGreaterThanOrEqualTo(0);
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }
    }

    @Nested
    @DisplayName("resolveSpecOutputDir")
    class ResolveSpecOutputDir {

        @Test
        @DisplayName("resolves from system property")
        void resolvesFromSystemProperty() {
            System.setProperty("punit.spec.dir", "/custom/path");
            try {
                assertThat(executor.resolveSpecOutputDir()).hasToString("/custom/path");
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("falls back to default when no property or env var is set")
        void fallsBackToDefault() {
            // Ensure no system property is set
            System.clearProperty("punit.spec.dir");

            Path result = executor.resolveSpecOutputDir();

            // Either PUNIT_SPEC_DIR env var or default
            if (System.getenv("PUNIT_SPEC_DIR") == null) {
                assertThat(result).hasToString("punit/specs");
            }
        }
    }
}
