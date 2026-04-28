package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.javai.punit.api.typed.spec.BaselineProvider;
import org.javai.punit.engine.baseline.YamlBaselineProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("BaselineProviderResolver — system property + project convention precedence")
class BaselineProviderResolverTest {

    private String savedProperty;

    @BeforeEach
    void clearProperty() {
        savedProperty = System.getProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
        System.clearProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        if (savedProperty == null) {
            System.clearProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY);
        } else {
            System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, savedProperty);
        }
    }

    @Test
    @DisplayName("system property pointing at an existing directory wins")
    void systemPropertyWinsWhenDirectoryExists(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir);
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, tempDir.toString());

        assertThat(BaselineProviderResolver.resolveDir()).contains(tempDir);
        assertThat(BaselineProviderResolver.resolve()).isInstanceOf(YamlBaselineProvider.class);
    }

    @Test
    @DisplayName("system property pointing at a missing directory falls back to EMPTY")
    void missingPropertyTargetFallsBackToEmpty(@TempDir Path tempDir) {
        System.setProperty(
                BaselineProviderResolver.BASELINE_DIR_PROPERTY,
                tempDir.resolve("does-not-exist").toString());

        assertThat(BaselineProviderResolver.resolveDir()).isEmpty();
        assertThat(BaselineProviderResolver.resolve()).isSameAs(BaselineProvider.EMPTY);
    }

    @Test
    @DisplayName("with no property set, falls back to convention or EMPTY")
    void noPropertyConventionOrEmpty() {
        // Convention dir resolution depends on the current working directory.
        // We assert only the consistency between resolveDir and resolve.
        var dir = BaselineProviderResolver.resolveDir();
        if (dir.isPresent()) {
            assertThat(BaselineProviderResolver.resolve()).isInstanceOf(YamlBaselineProvider.class);
        } else {
            assertThat(BaselineProviderResolver.resolve()).isSameAs(BaselineProvider.EMPTY);
        }
    }
}
