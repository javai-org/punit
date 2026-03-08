package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Optional;
import org.javai.punit.api.ProbabilisticTest;
import org.javai.punit.ptest.bernoulli.FinalVerdictDecider;
import org.javai.punit.ptest.engine.ConfigurationResolver;
import org.javai.punit.reporting.VerdictEvent;
import org.javai.punit.sentinel.testsubjects.FailingSentinel;
import org.javai.punit.sentinel.testsubjects.PassingSentinel;
import org.javai.punit.spec.registry.LayeredSpecRepository;
import org.javai.punit.statistics.ThresholdDeriver;
import org.javai.punit.usecase.UseCaseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SentinelTestExecutorTest {

    private SentinelTestExecutor executor;
    private SentinelClassIntrospector introspector;

    @BeforeEach
    void setUp() {
        introspector = new SentinelClassIntrospector();
        executor = new SentinelTestExecutor(
                new SentinelSampleExecutor(),
                introspector,
                new ConfigurationResolver(LayeredSpecRepository.createDefault()),
                new ThresholdDeriver(),
                new FinalVerdictDecider(),
                new EnvironmentMetadata("test-env", "test-instance"));
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("returns PASS verdict for passing use case")
        void passingVerdict() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);
            Optional<String> useCaseId = executor.resolveUseCaseId(annotation);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, useCaseId);

            assertThat(verdict.passed()).isTrue();
            assertThat(verdict.testName()).isEqualTo("PassingSentinel.testStub");
            assertThat(verdict.useCaseId()).isEqualTo("stub-use-case");
        }

        @Test
        @DisplayName("returns FAIL verdict for failing use case")
        void failingVerdict() {
            FailingSentinel sentinel = new FailingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(FailingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);
            Optional<String> useCaseId = executor.resolveUseCaseId(annotation);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    FailingSentinel.class, useCaseId);

            assertThat(verdict.passed()).isFalse();
            assertThat(verdict.testName()).isEqualTo("FailingSentinel.testFailing");
            assertThat(verdict.useCaseId()).isEqualTo("failing-use-case");
        }

        @Test
        @DisplayName("includes environment metadata in verdict")
        void includesEnvironmentMetadata() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.environmentMetadata())
                    .containsEntry("environment", "test-env")
                    .containsEntry("instance", "test-instance");
        }

        @Test
        @DisplayName("generates correlation ID on each verdict")
        void generatesCorrelationId() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent first = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));
            VerdictEvent second = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(first.correlationId()).startsWith("v:");
            assertThat(first.correlationId()).isNotEqualTo(second.correlationId());
        }
    }

    @Nested
    @DisplayName("report entries")
    class ReportEntries {

        @Test
        @DisplayName("contains all expected keys for passing test")
        void passingReportEntries() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.reportEntries()).containsKeys(
                    "punit.samples", "punit.samplesExecuted",
                    "punit.successes", "punit.failures",
                    "punit.minPassRate", "punit.observedPassRate",
                    "punit.verdict", "punit.terminationReason",
                    "punit.elapsedMs");
        }

        @Test
        @DisplayName("records PASS verdict in report entries")
        void passVerdictInEntries() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.reportEntries()).containsEntry("punit.verdict", "PASS");
        }

        @Test
        @DisplayName("records FAIL verdict in report entries")
        void failVerdictInEntries() {
            FailingSentinel sentinel = new FailingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(FailingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    FailingSentinel.class, Optional.of("failing-use-case"));

            assertThat(verdict.reportEntries()).containsEntry("punit.verdict", "FAIL");
        }

        @Test
        @DisplayName("records per-dimension functional results")
        void functionalDimensionTracking() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.reportEntries())
                    .containsEntry("punit.dimension.functional", "true");
            assertThat(verdict.reportEntries())
                    .containsKey("punit.dimension.functional.successes");
        }

        @Test
        @DisplayName("reports correct sample counts")
        void sampleCounts() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.reportEntries().get("punit.samples")).isEqualTo("10");
            assertThat(Integer.parseInt(verdict.reportEntries().get("punit.successes")))
                    .isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("resolveUseCaseId")
    class ResolveUseCaseId {

        @Test
        @DisplayName("resolves use case ID from annotation")
        void resolvesFromAnnotation() {
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            Optional<String> useCaseId = executor.resolveUseCaseId(annotation);

            assertThat(useCaseId).hasValue("stub-use-case");
        }

        @Test
        @DisplayName("resolves failing use case ID from annotation")
        void resolvesFailingUseCase() {
            Method method = introspector.findTestMethods(FailingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            Optional<String> useCaseId = executor.resolveUseCaseId(annotation);

            assertThat(useCaseId).hasValue("failing-use-case");
        }
    }

    @Nested
    @DisplayName("test name formatting")
    class TestNameFormatting {

        @Test
        @DisplayName("formats test name as ClassName.methodName")
        void formatsTestName() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.testName()).isEqualTo("PassingSentinel.testStub");
        }

        @Test
        @DisplayName("uses use case ID when available, test name as fallback")
        void useCaseIdFallback() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            VerdictEvent withId = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));
            VerdictEvent withoutId = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.empty());

            assertThat(withId.useCaseId()).isEqualTo("stub-use-case");
            assertThat(withoutId.useCaseId()).isEqualTo("PassingSentinel.testStub");
        }
    }
}
