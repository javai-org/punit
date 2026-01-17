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
 * Architecture tests to enforce package dependency rules.
 *
 * <p>PUnit has three main architectural pillars:
 * <ul>
 *   <li>{@code experiment/} - Running experiments to gather empirical data</li>
 *   <li>{@code spec/} - Specifications, baselines, and supporting infrastructure</li>
 *   <li>{@code ptest/} - Running probabilistic tests against specs</li>
 * </ul>
 *
 * <p>These pillars should not have cross-dependencies except through shared
 * infrastructure in {@code api/}, {@code model/}, or {@code engine/} (shared).
 */
class PackageDependencyTest {

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
            // After pacing refactor: experiment no longer depends on ptest.engine
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
    @DisplayName("Shared Infrastructure")
    class SharedInfrastructure {

        @Test
        @DisplayName("api package should not depend on ptest strategy internals")
        void apiShouldNotDependOnPtestStrategyInternals() {
            // Note: api annotations MUST reference engine extensions via @ExtendedWith
            // This is how JUnit 5 extension registration works.
            // Example: @ProbabilisticTest uses @ExtendedWith(ProbabilisticTestExtension.class)
            //
            // Also: api may reference experiment.optimize types for @OptimizeExperiment
            // parameters (FactorMutator, Scorer, etc.) - these are part of the public API.
            //
            // The constraint: api should not depend on ptest strategy internals
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ptest.strategy..", "..ptest.bernoulli..");

            rule.check(classes);
        }

        @Test
        @DisplayName("model package should not depend on execution packages")
        void modelShouldNotDependOnExecution() {
            // Model package contains data structures, not execution logic
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..model..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ptest.engine..", "..experiment.engine..");

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Shared Engine Infrastructure")
    class SharedEngineInfrastructure {

        @Test
        @DisplayName("engine.pacing should be usable by both experiment and ptest")
        void enginePacingShouldBeShared() {
            // Pacing classes live in org.javai.punit.engine.pacing
            // Both experiment and ptest can depend on them
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..engine.pacing..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..experiment..", "..ptest..");

            rule.check(classes);
        }
    }
}
