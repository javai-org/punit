package org.javai.punit.api.typed.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for the {@code punit-api} module.
 *
 * <p>{@code punit-api} is the thin public authoring surface. It must
 * not drag in any framework internals and must not depend on JUnit —
 * authors and downstream consumers must be able to use these types
 * without JUnit on the classpath.
 */
@DisplayName("punit-api Architecture Rules")
class PunitApiArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit.api.typed");
    }

    @Test
    @DisplayName("punit-api classes must not depend on JUnit")
    void mustNotDependOnJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.api.typed..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..")
                .because("punit-api is a JUnit-free author-facing surface");
        rule.check(classes);
    }

    @Test
    @DisplayName("punit-api classes must not depend on any other punit package")
    void mustNotDependOnInternalPunit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.api.typed..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.javai.punit.ptest..",
                        "org.javai.punit.experiment..",
                        "org.javai.punit.statistics..",
                        "org.javai.punit.spec..",
                        "org.javai.punit.model..",
                        "org.javai.punit.controls..",
                        "org.javai.punit.reporting..",
                        "org.javai.punit.verdict..",
                        "org.javai.punit.contract..",
                        "org.javai.punit.usecase..",
                        "org.javai.punit.util.."
                )
                .because("the typed API points outward, not to framework internals");
        rule.check(classes);
    }
}
