package org.javai.punit.ptest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.engine.covariate.BaselineRepository;
import org.javai.punit.engine.covariate.BaselineSelector;
import org.javai.punit.engine.covariate.CovariateProfileResolver;
import org.javai.punit.engine.covariate.FootprintComputer;
import org.javai.punit.engine.covariate.UseCaseCovariateExtractor;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.ptest.engine.BaselineSelectionOrchestrator.PendingSelection;
import org.javai.punit.ptest.engine.BaselineSelectionOrchestrator.PreparationResult;
import org.javai.punit.reporting.PUnitReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests for {@link BaselineSelectionOrchestrator}.
 */
class BaselineSelectionOrchestratorTest {

    private BaselineSelectionOrchestrator orchestrator;
    private ConfigurationResolver configResolver;
    private BaselineRepository baselineRepository;
    private BaselineSelector baselineSelector;
    private CovariateProfileResolver covariateProfileResolver;
    private FootprintComputer footprintComputer;
    private UseCaseCovariateExtractor covariateExtractor;
    private PUnitReporter reporter;

    @BeforeEach
    void setUp() {
        configResolver = new ConfigurationResolver();
        baselineRepository = new BaselineRepository();
        baselineSelector = new BaselineSelector();
        covariateProfileResolver = new CovariateProfileResolver();
        footprintComputer = new FootprintComputer();
        covariateExtractor = new UseCaseCovariateExtractor();
        reporter = new PUnitReporter();

        orchestrator = new BaselineSelectionOrchestrator(
                configResolver,
                baselineRepository,
                baselineSelector,
                covariateProfileResolver,
                footprintComputer,
                covariateExtractor,
                reporter
        );
    }

    @Nested
    @DisplayName("PreparationResult")
    class PreparationResultTests {

        @Test
        @DisplayName("ofPending creates result with pending selection")
        void ofPendingCreatesResultWithPending() {
            PendingSelection pending = new PendingSelection(
                    "TestUseCase",
                    Object.class,
                    CovariateDeclaration.EMPTY,
                    "footprint-abc",
                    List.of()
            );

            PreparationResult result = PreparationResult.ofPending(pending);

            assertThat(result.hasPending()).isTrue();
            assertThat(result.hasSpec()).isFalse();
            assertThat(result.pending()).isSameAs(pending);
        }

        @Test
        @DisplayName("none creates empty result")
        void noneCreatesEmptyResult() {
            PreparationResult result = PreparationResult.none();

            assertThat(result.hasPending()).isFalse();
            assertThat(result.hasSpec()).isFalse();
        }
    }

    @Nested
    @DisplayName("PendingSelection")
    class PendingSelectionTests {

        @Test
        @DisplayName("rejects null specId")
        void rejectsNullSpecId() {
            assertThatThrownBy(() -> new PendingSelection(
                    null,
                    Object.class,
                    CovariateDeclaration.EMPTY,
                    "footprint",
                    List.of()
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("specId");
        }

        @Test
        @DisplayName("rejects null useCaseClass")
        void rejectsNullUseCaseClass() {
            assertThatThrownBy(() -> new PendingSelection(
                    "TestUseCase",
                    null,
                    CovariateDeclaration.EMPTY,
                    "footprint",
                    List.of()
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("useCaseClass");
        }

        @Test
        @DisplayName("creates valid pending selection")
        void createsValidPendingSelection() {
            PendingSelection pending = new PendingSelection(
                    "TestUseCase",
                    Object.class,
                    CovariateDeclaration.EMPTY,
                    "footprint-123",
                    List.of()
            );

            assertThat(pending.specId()).isEqualTo("TestUseCase");
            assertThat(pending.useCaseClass()).isEqualTo(Object.class);
            assertThat(pending.footprint()).isEqualTo("footprint-123");
        }
    }

    @Nested
    @DisplayName("findUseCaseProvider")
    class FindUseCaseProvider {

        @Test
        @DisplayName("returns empty when no provider exists")
        void returnsEmptyWhenNoProvider() {
            Object testInstance = new Object();

            Optional<UseCaseProvider> result = orchestrator.findUseCaseProvider(
                    testInstance, testInstance.getClass());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("finds instance field provider")
        void findsInstanceFieldProvider() {
            TestClassWithInstanceProvider testInstance = new TestClassWithInstanceProvider();

            Optional<UseCaseProvider> result = orchestrator.findUseCaseProvider(
                    testInstance, testInstance.getClass());

            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(testInstance.provider);
        }

        @Test
        @DisplayName("finds static field provider when no instance")
        void findsStaticFieldProviderWhenNoInstance() {
            Optional<UseCaseProvider> result = orchestrator.findUseCaseProvider(
                    null, TestClassWithStaticProvider.class);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("finds provider from instance with both instance and static fields")
        void findsProviderFromInstanceWithBothFields() {
            TestClassWithBothProviders testInstance = new TestClassWithBothProviders();

            Optional<UseCaseProvider> result = orchestrator.findUseCaseProvider(
                    testInstance, testInstance.getClass());

            assertThat(result).isPresent();
            // Should find a provider (either instance or static - both are valid)
            assertThat(result.get()).isNotNull();
        }

        // Test helper classes
        static class TestClassWithInstanceProvider {
            UseCaseProvider provider = new UseCaseProvider();
        }

        static class TestClassWithStaticProvider {
            @RegisterExtension
            static UseCaseProvider staticProvider = new UseCaseProvider();
        }

        static class TestClassWithBothProviders {
            static UseCaseProvider staticProvider = new UseCaseProvider();
            UseCaseProvider instanceProvider = new UseCaseProvider();
        }
    }

    @Nested
    @DisplayName("resolveUseCaseInstance")
    class ResolveUseCaseInstance {

        @Test
        @DisplayName("returns null when provider is null")
        void returnsNullWhenProviderNull() {
            Object result = orchestrator.resolveUseCaseInstance(null, Object.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null when class not registered")
        void returnsNullWhenClassNotRegistered() {
            UseCaseProvider provider = new UseCaseProvider();

            Object result = orchestrator.resolveUseCaseInstance(provider, String.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns instance when registered")
        void returnsInstanceWhenRegistered() {
            UseCaseProvider provider = new UseCaseProvider();
            String expectedInstance = "test-instance";
            provider.register(String.class, () -> expectedInstance);

            Object result = orchestrator.resolveUseCaseInstance(provider, String.class);

            assertThat(result).isSameAs(expectedInstance);
        }
    }
}

