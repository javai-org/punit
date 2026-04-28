package org.javai.punit.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    @DisplayName("system property takes precedence over the convention default")
    void systemPropertyTakesPrecedence(@TempDir Path tempDir) {
        Files.exists(tempDir);
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, tempDir.toString());

        assertThat(BaselineProviderResolver.resolveDir()).isEqualTo(tempDir);
        assertThat(BaselineProviderResolver.resolve()).isInstanceOf(YamlBaselineProvider.class);
    }

    @Test
    @DisplayName("with no property set, falls back to the convention path")
    void noPropertyConvention() {
        assertThat(BaselineProviderResolver.resolveDir())
                .isEqualTo(Paths.get(BaselineProviderResolver.CONVENTION_PATH));
        assertThat(BaselineProviderResolver.resolve()).isInstanceOf(YamlBaselineProvider.class);
    }

    @Test
    @DisplayName("returns the configured directory regardless of whether it currently exists")
    void returnsDirWhenMissing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist-yet");
        System.setProperty(BaselineProviderResolver.BASELINE_DIR_PROPERTY, missing.toString());

        assertThat(BaselineProviderResolver.resolveDir()).isEqualTo(missing);
        // Provider is constructed unconditionally; underlying lookups
        // return empty for any query against a non-existent directory.
        assertThat(BaselineProviderResolver.resolve()).isInstanceOf(YamlBaselineProvider.class);
    }
}
