package org.javai.punit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests to enforce PUnit's structural constraints.
 *
 * <p>PUnit has three main architectural pillars:
 * <ul>
 *   <li>{@code experiment/} - Running experiments to gather empirical data</li>
 *   <li>{@code spec/} - Specifications, baselines, and supporting infrastructure</li>
 *   <li>{@code ptest/} - Running probabilistic tests against specs</li>
 * </ul>
 *
 * <p>These pillars should not have cross-dependencies except through shared
 * infrastructure in {@code api/}, {@code model/}, or {@code controls/} (pacing, budget).
 *
 * <p>Additional rules enforce:
 * <ul>
 *   <li>Statistics module isolation for independent scrutiny</li>
 *   <li>Dependencies flow in expected directions</li>
 * </ul>
 *
 * <p><strong>Note:</strong> The {@code punit-sentinel} module is not on this module's classpath,
 * so its classes are not scanned here. When {@code punit-sentinel} gains production code
 * (Phase 4), it must have its own ArchUnit test enforcing zero {@code org.junit} dependencies.
 *
 * @see <a href="file:../../../../../plan/DOC-03-ARCHITECTURE-OVERVIEW.md">Architecture Overview</a>
 */
@DisplayName("Architecture Rules")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit");
    }

    @Nested
    @DisplayName("Pillar Independence")
    class PillarIndependence {

        @Test
        @DisplayName("experiment package should not depend on ptest.engine")
        void experimentShouldNotDependOnPtestEngine() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..experiment..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..ptest.engine..");

            rule.check(classes);
        }

        @Test
        @DisplayName("ptest package should not depend on experiment internals")
        void ptestShouldNotDependOnExperimentInternals() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..ptest..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..experiment.measure..", "..experiment.explore..", "..experiment.optimize..");

            rule.check(classes);
        }

        @Test
        @DisplayName("experiment strategies should not depend on ptest strategies")
        void experimentStrategiesShouldNotDependOnPtestStrategies() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage("..experiment.measure..", "..experiment.explore..", "..experiment.optimize..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ptest.bernoulli..", "..ptest.strategy..");

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Controls Infrastructure")
    class ControlsInfrastructure {

        @Test
        @DisplayName("controls.pacing should not depend on experiment or ptest")
        void controlsPacingShouldBeIndependent() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..controls.pacing..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..experiment..", "..ptest..");

            rule.check(classes);
        }

        @Test
        @DisplayName("controls.budget should not depend on experiment or ptest")
        void controlsBudgetShouldBeIndependent() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..controls.budget..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..experiment..", "..ptest..");

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Shared Infrastructure")
    class SharedInfrastructure {

        @Test
        @DisplayName("api package should not depend on ptest strategy internals")
        void apiShouldNotDependOnPtestStrategyInternals() {
            // api annotations are pure markers: they carry only JUnit annotation
            // types (@TestTemplate, @Tag) as meta-annotations. JUnit extension
            // wiring happens via ServiceLoader auto-registration, not via
            // @ExtendWith on the user-facing annotations.
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ptest.strategy..", "..ptest.bernoulli..");

            rule.check(classes);
        }

        @Test
        @DisplayName("model package should not depend on execution packages")
        void modelShouldNotDependOnExecution() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..model..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ptest.engine..", "..experiment.engine..");

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Abstraction Level Enforcement")
    class AbstractionLevelEnforcement {

        /**
         * Evaluators, resolvers, and deciders are computation/decision classes.
         * They must not depend on reporting infrastructure (formatting, rendering).
         * Formatting belongs in dedicated formatter/renderer/messages classes.
         */
        @Test
        @DisplayName("Evaluators, resolvers, and deciders must not depend on reporting")
        void evaluatorsResolversDecidersMustNotDependOnReporting() {
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("Evaluator")
                    .or().haveSimpleNameEndingWith("Resolver")
                    .or().haveSimpleNameEndingWith("Decider")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..reporting..")
                    .because("evaluators/resolvers/deciders compute decisions; "
                            + "formatting belongs in dedicated formatter/renderer classes");

            rule.check(classes);
        }

        /**
         * Strategy classes orchestrate test execution. They receive reporters
         * via injection, not by instantiating them directly. This prevents
         * strategies from being coupled to specific reporter implementations.
         */
        @Test
        @DisplayName("Strategies must not instantiate PUnitReporter")
        void strategiesMustNotInstantiatePUnitReporter() {
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("Strategy")
                    .should().dependOnClassesThat()
                    .haveSimpleName("PUnitReporter")
                    .because("strategies receive reporters via injection, "
                            + "not by instantiating them directly");

            rule.check(classes);
        }

        /**
         * Renderers format pre-computed data for display. They must not
         * perform statistical computation themselves — that belongs in
         * estimators and derivers.
         */
        @Test
        @DisplayName("Renderers must not depend on statistical computation classes")
        void renderersMustNotDependOnStatisticalComputation() {
            ArchRule rule = noClasses()
                    .that().haveSimpleNameEndingWith("Renderer")
                    .should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Estimator")
                    .orShould().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Deriver")
                    .because("renderers format pre-computed data; "
                            + "statistical computation belongs in estimator/deriver classes");

            rule.check(classes);
        }

    }

}
