package org.javai.punit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architectural invariant: the {@code org.javai.punit.api} package in
 * {@code punit-junit5} must contain no annotation declarations.
 *
 * <p>All user-facing PUnit annotations live in {@code punit-core/api}, so they
 * are available to JUnit-free runtimes such as Sentinel. {@code punit-junit5/api}
 * is reserved for JUnit-specific public types (e.g. {@code UseCaseProvider}).
 *
 * <p>This rule is filesystem-based rather than ArchUnit-based because ArchUnit
 * inspects the unified classpath and cannot distinguish between annotations
 * declared in {@code punit-core} and annotations declared in {@code punit-junit5}.
 *
 * <p>See {@code plan/DES-ANNOTATIONS-REFACTOR.md} for the rationale.
 */
@DisplayName("punit-junit5/api Contents Rules")
class Junit5ApiPackageContentsTest {

    private static final Path API_DIR = Path.of(
            "src/main/java/org/javai/punit/api");

    private static final Pattern ANNOTATION_DECLARATION =
            Pattern.compile("(?m)^\\s*public\\s+@interface\\b");

    @Test
    @DisplayName("no @interface declarations exist in punit-junit5/api")
    void noAnnotationDeclarationsInJunit5Api() throws IOException {
        if (!Files.isDirectory(API_DIR)) {
            return;
        }
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(API_DIR)) {
            offenders = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Junit5ApiPackageContentsTest::declaresAnnotation)
                    .toList();
        }
        assertThat(offenders)
                .as("user-facing annotations must live in punit-core/api, "
                        + "not punit-junit5/api — see plan/DES-ANNOTATIONS-REFACTOR.md")
                .isEmpty();
    }

    private static boolean declaresAnnotation(Path javaFile) {
        try {
            String source = Files.readString(javaFile);
            return ANNOTATION_DECLARATION.matcher(source).find();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + javaFile, e);
        }
    }
}
