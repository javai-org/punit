package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.reporting.VerdictEvent;
import org.javai.punit.sentinel.testsubjects.ExperimentSentinel;
import org.javai.punit.sentinel.testsubjects.StubUseCase;
import org.javai.punit.usecase.UseCaseFactory;
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

                VerdictEvent verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.passed()).isTrue();
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("formats experiment name as ClassName.methodName")
        void formatsExperimentName(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                VerdictEvent verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.testName()).isEqualTo("ExperimentSentinel.measureStub");
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

                VerdictEvent verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.useCaseId()).isEqualTo("stub-use-case");
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

                VerdictEvent verdict = executor.execute(
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

                VerdictEvent verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.passed()).isTrue();
                assertThat(nestedDir).isDirectory();
                assertThat(nestedDir.resolve("stub-use-case.yaml")).exists();
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }
    }

    @Nested
    @DisplayName("report entries")
    class ReportEntries {

        @Test
        @DisplayName("contains all expected experiment keys")
        void containsExpectedKeys(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                VerdictEvent verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.reportEntries()).containsKeys(
                        "punit.experiment.useCaseId",
                        "punit.experiment.samples",
                        "punit.experiment.successes",
                        "punit.experiment.failures",
                        "punit.experiment.verdict");
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("reports correct sample count")
        void reportsSampleCount(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                VerdictEvent verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.reportEntries().get("punit.experiment.samples")).isEqualTo("5");
                assertThat(verdict.reportEntries().get("punit.experiment.useCaseId"))
                        .isEqualTo("stub-use-case");
            } finally {
                System.clearProperty("punit.spec.dir");
            }
        }

        @Test
        @DisplayName("records PASS verdict in entries for successful experiment")
        void passVerdictInEntries(@TempDir Path tempDir) {
            System.setProperty("punit.spec.dir", tempDir.toString());
            try {
                ExperimentSentinel sentinel = new ExperimentSentinel();
                UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
                Method method = introspector.findExperimentMethods(ExperimentSentinel.class).getFirst();
                MeasureExperiment annotation = method.getAnnotation(MeasureExperiment.class);

                VerdictEvent verdict = executor.execute(
                        method, annotation, sentinel, factory, ExperimentSentinel.class);

                assertThat(verdict.reportEntries().get("punit.experiment.verdict")).isEqualTo("PASS");
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
