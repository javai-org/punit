package org.javai.punit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture tests specific to the example code.
 *
 * <p>The {@code infrastructure} package contains classes that represent application
 * code — payment gateways, LLM clients, domain models — and must not depend on
 * any PUnit framework packages. This mirrors the real-world constraint: your
 * application infrastructure is independent of your test framework.
 */
@DisplayName("Example Architecture Rules")
class ExampleArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("org.javai.punit.examples.app");
    }

    @Test
    @DisplayName("Infrastructure must not depend on PUnit framework")
    void infrastructureMustNotDependOnPunitFramework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.examples.app..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.javai.punit.api..",
                        "org.javai.punit.ptest..",
                        "org.javai.punit.experiment..",
                        "org.javai.punit.spec..",
                        "org.javai.punit.statistics..",
                        "org.javai.punit.model..",
                        "org.javai.punit.controls..",
                        "org.javai.punit.reporting..",
                        "org.javai.punit.engine.."
                )
                .because("infrastructure represents application code that is independent of the test framework");

        rule.check(classes);
    }
}
