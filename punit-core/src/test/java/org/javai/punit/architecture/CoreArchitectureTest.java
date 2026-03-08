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
 * Architecture tests enforcing punit-core's JUnit independence.
 *
 * <p>punit-core is the JUnit-free statistical engine. It must never depend on
 * JUnit extension or engine types. The only permitted JUnit usage is
 * {@code @TestTemplate} and {@code @Tag} meta-annotations on PUnit's own
 * annotations in the {@code api} package — these are inert without the
 * JUnit engine on the classpath (DD-05).
 */
@DisplayName("punit-core Architecture Rules")
class CoreArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit");
    }

    @Nested
    @DisplayName("JUnit Independence")
    class JUnitIndependence {

        @Test
        @DisplayName("non-api packages must not depend on JUnit at all")
        void nonApiPackagesMustNotDependOnJUnit() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit..")
                    .and().resideOutsideOfPackage("org.javai.punit.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.junit..")
                    .because("punit-core (outside api) must be completely JUnit-free "
                            + "to support Sentinel and other non-JUnit engines");

            rule.check(classes);
        }

        @Test
        @DisplayName("api package must not depend on JUnit extension API")
        void apiPackageMustNotDependOnJUnitExtensions() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.junit.jupiter.api.extension..",
                            "org.junit.jupiter.engine..",
                            "org.junit.platform.."
                    )
                    .because("api annotations may only reference JUnit annotation types "
                            + "(@TestTemplate, @Tag) as meta-annotations — never extension or engine types");

            rule.check(classes);
        }

        /**
         * The usecase package in punit-core must not depend on JUnit at all.
         * This ensures UseCaseFactory remains usable outside JUnit (e.g. Sentinel).
         */
        @Test
        @DisplayName("usecase package must not depend on JUnit")
        void useCaseFactoryMustNotDependOnJUnit() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.usecase..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.junit..")
                    .because("usecase package is in punit-core and must be JUnit-free for Sentinel support");

            rule.check(classes);
        }

        /**
         * punit-core packages (statistics, model, usecase, contract) must not depend
         * on JUnit extension types. The controls package is excluded because
         * ProbabilisticTestBudgetExtension in punit-junit5 shares the controls.budget
         * package namespace.
         */
        @Test
        @DisplayName("punit-core packages must not depend on JUnit extension API")
        void corePackagesMustNotDependOnJUnitExtensions() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(
                            "org.javai.punit.statistics..",
                            "org.javai.punit.model..",
                            "org.javai.punit.usecase..",
                            "org.javai.punit.contract.."
                    )
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.junit.jupiter.api.extension..")
                    .because("punit-core packages must be JUnit-free to support Sentinel and other engines");

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Module Isolation")
    class ModuleIsolation {

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
                            "org.javai.punit.model.."
                    );

            rule.check(classes);
        }

        /**
         * The util package contains general-purpose utilities (hashing, lazy evaluation).
         * It must not depend on any framework-specific packages.
         */
        @Test
        @DisplayName("util package must be self-contained")
        void utilPackageMustBeSelfContained() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..util..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.javai.punit.api..",
                            "org.javai.punit.ptest..",
                            "org.javai.punit.experiment..",
                            "org.javai.punit.spec..",
                            "org.javai.punit.statistics..",
                            "org.javai.punit.model..",
                            "org.javai.punit.controls..",
                            "org.javai.punit.reporting.."
                    )
                    .because("utilities must be self-contained and not depend on framework internals");

            rule.check(classes);
        }

        /**
         * The usecase package in punit-core must not depend on JUnit engine types.
         * It provides JUnit-free factory logic for use case creation.
         */
        @Test
        @DisplayName("usecase package must not depend on JUnit engine types")
        void useCaseFactoryMustNotDependOnJUnitEngine() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("org.javai.punit.usecase..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.javai.punit.ptest.engine..",
                            "org.javai.punit.experiment.engine.."
                    )
                    .because("usecase package is in punit-core and must not depend on JUnit engine layer");

            rule.check(classes);
        }
    }
}
