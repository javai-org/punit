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
 *   <li>Core packages must not depend on LLM extension (llmx)</li>
 *   <li>Statistics module isolation for independent scrutiny</li>
 *   <li>Dependencies flow in expected directions</li>
 * </ul>
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
            // Note: api annotations MUST reference engine extensions via @ExtendedWith.
            // This is how JUnit 5 extension registration works.
            // The constraint: api should not depend on ptest strategy internals.
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
    @DisplayName("LLM Extension Isolation")
    class LlmExtensionIsolation {

        @Test
        @DisplayName("Core API must not depend on LLM extension (llmx)")
        void coreApiMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(classes);
        }

        @Test
        @DisplayName("Core engine must not depend on LLM extension (llmx)")
        void coreEngineMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.ptest.engine..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(classes);
        }

        @Test
        @DisplayName("Experiment module must not depend on LLM extension (llmx)")
        void experimentMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.experiment..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(classes);
        }

        @Test
        @DisplayName("Specification module must not depend on LLM extension (llmx)")
        void specMustNotDependOnLlmx() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.spec..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.javai.punit.llmx..");

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Statistics Module Isolation")
    class StatisticsModuleIsolation {

        /**
         * The statistics module is intentionally isolated from all other PUnit packages.
         *
         * <p>This isolation enables:
         * <ul>
         *   <li><strong>Independent scrutiny:</strong> Statisticians can review calculations
         *       without understanding the broader framework.</li>
         *   <li><strong>Rigorous testing:</strong> Statistical concepts have dedicated unit tests
         *       with worked examples using real-world variable names.</li>
         *   <li><strong>Trust building:</strong> Calculations map directly to formulations in
         *       the STATISTICAL-COMPANION document.</li>
         * </ul>
         */
        @Test
        @DisplayName("Statistics module must not depend on any framework packages")
        void statisticsModuleMustBeIsolated() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.statistics..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.javai.punit.api..",
                            "org.javai.punit.ptest.engine..",
                            "org.javai.punit.experiment..",
                            "org.javai.punit.spec..",
                            "org.javai.punit.model..",
                            "org.javai.punit.llmx.."
                    );

            rule.check(classes);
        }
    }
}
