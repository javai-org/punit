package org.javai.punit.runtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for {@code org.javai.punit.runtime}.
 *
 * <p>The runtime package hosts the authoring entry point
 * ({@code PUnit}) and its supporting classes. It must remain
 * importable from a sentinel-deployed class — i.e. without
 * dragging in JUnit Jupiter or JUnit Platform on the runtime
 * classpath.
 *
 * <p>Class-level rule: nothing in {@code org.javai.punit.runtime} may
 * import any class in {@code org.junit..}. The {@code opentest4j}
 * exception types ({@link org.opentest4j.AssertionFailedError},
 * {@link org.opentest4j.TestAbortedException}) are permitted — they
 * are the standard contract for non-JUnit test-failure signalling
 * and carry no JUnit Platform dependency themselves.
 */
@DisplayName("runtime package architecture rules")
class RuntimeArchitectureTest {

    private static JavaClasses runtimeClasses;

    @BeforeAll
    static void importClasses() {
        runtimeClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.javai.punit.runtime");
    }

    @Test
    @DisplayName("runtime classes must not import any JUnit type")
    void runtimeMustNotImportJUnit() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("org.javai.punit.runtime..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..")
                .because("the runtime package is the JUnit-free authoring runtime — "
                        + "it must be reachable from a sentinel-deployed class "
                        + "without bringing JUnit Jupiter or JUnit Platform along");

        rule.check(runtimeClasses);
    }
}
