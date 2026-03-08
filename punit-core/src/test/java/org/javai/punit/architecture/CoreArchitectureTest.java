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
    }
}
