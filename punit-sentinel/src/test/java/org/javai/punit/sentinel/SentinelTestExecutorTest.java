package org.javai.punit.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Optional;
import org.javai.punit.api.legacy.ProbabilisticTest;
import org.javai.punit.ptest.bernoulli.FinalVerdictDecider;
import org.javai.punit.ptest.engine.ConfigurationResolver;
import org.javai.punit.sentinel.testsubjects.FailingSentinel;
import org.javai.punit.sentinel.testsubjects.PassingSentinel;
import org.javai.punit.spec.registry.LayeredSpecRepository;
import org.javai.punit.statistics.ThresholdDeriver;
import org.javai.punit.usecase.UseCaseFactory;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.PunitVerdict;
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

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, useCaseId);

            assertThat(verdict.junitPassed()).isTrue();
            assertThat(verdict.identity().className()).isEqualTo(PassingSentinel.class.getName());
            assertThat(verdict.identity().methodName()).isEqualTo("testStub");
            assertThat(verdict.identity().useCaseId()).hasValue("stub-use-case");
        }

        @Test
        @DisplayName("returns FAIL verdict for failing use case")
        void failingVerdict() {
            FailingSentinel sentinel = new FailingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(FailingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);
            Optional<String> useCaseId = executor.resolveUseCaseId(annotation);

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    FailingSentinel.class, useCaseId);

            assertThat(verdict.junitPassed()).isFalse();
            assertThat(verdict.identity().className()).isEqualTo(FailingSentinel.class.getName());
            assertThat(verdict.identity().methodName()).isEqualTo("testFailing");
            assertThat(verdict.identity().useCaseId()).hasValue("failing-use-case");
        }

        @Test
        @DisplayName("includes environment metadata in verdict")
        void includesEnvironmentMetadata() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict verdict = executor.execute(
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

            ProbabilisticTestVerdict first = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));
            ProbabilisticTestVerdict second = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(first.correlationId()).startsWith("v:");
            assertThat(first.correlationId()).isNotEqualTo(second.correlationId());
        }
    }

    @Nested
    @DisplayName("verdict fields")
    class VerdictFields {

        @Test
        @DisplayName("contains execution summary for passing test")
        void passingExecutionSummary() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.execution().plannedSamples()).isEqualTo(10);
            assertThat(verdict.execution().samplesExecuted()).isGreaterThan(0);
            assertThat(verdict.execution().successes()).isGreaterThan(0);
        }

        @Test
        @DisplayName("records PASS punit verdict for passing test")
        void passVerdictForPassingTest() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.punitVerdict()).isEqualTo(PunitVerdict.PASS);
        }

        @Test
        @DisplayName("records FAIL punit verdict for failing test")
        void failVerdictForFailingTest() {
            FailingSentinel sentinel = new FailingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(FailingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    FailingSentinel.class, Optional.of("failing-use-case"));

            assertThat(verdict.punitVerdict()).isEqualTo(PunitVerdict.FAIL);
        }

        @Test
        @DisplayName("records per-dimension functional results")
        void functionalDimensionTracking() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.functional()).isPresent();
            assertThat(verdict.functional().get().successes()).isGreaterThan(0);
        }

        @Test
        @DisplayName("reports correct sample counts")
        void sampleCounts() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.execution().plannedSamples()).isEqualTo(10);
            assertThat(verdict.execution().successes()).isGreaterThan(0);
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
    @DisplayName("test identity")
    class TestIdentity {

        @Test
        @DisplayName("identity uses fully qualified class name and method name")
        void identityFields() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict verdict = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));

            assertThat(verdict.identity().className()).isEqualTo(PassingSentinel.class.getName());
            assertThat(verdict.identity().methodName()).isEqualTo("testStub");
        }

        @Test
        @DisplayName("uses use case ID when available")
        void useCaseIdPresent() {
            PassingSentinel sentinel = new PassingSentinel();
            UseCaseFactory factory = introspector.findUseCaseFactory(sentinel);
            Method method = introspector.findTestMethods(PassingSentinel.class).getFirst();
            ProbabilisticTest annotation = method.getAnnotation(ProbabilisticTest.class);

            ProbabilisticTestVerdict withId = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.of("stub-use-case"));
            ProbabilisticTestVerdict withoutId = executor.execute(
                    method, annotation, sentinel, factory,
                    PassingSentinel.class, Optional.empty());

            assertThat(withId.identity().useCaseId()).hasValue("stub-use-case");
            assertThat(withoutId.identity().useCaseId()).isEmpty();
        }
    }
}
