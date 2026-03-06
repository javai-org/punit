package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.javai.punit.model.CovariateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CustomCovariateResolver}.
 */
@DisplayName("CustomCovariateResolver")
class CustomCovariateResolverTest {

    @Nested
    @DisplayName("resolution order")
    class ResolutionOrderTests {

        @Test
        @DisplayName("should prefer system property over env var")
        void shouldPreferSystemPropertyOverEnvVar() {
            // Set up a system property
            String key = "test.covariate.custom";
            System.setProperty(key, "from-sysprop");
            
            try {
                var context = DefaultCovariateResolutionContext.builder()
                    .punitEnvironment(Map.of(key, "from-punit-env"))
                    .build();
                
                var resolver = new CustomCovariateResolver(key);
                var result = resolver.resolve(context);
                
                assertThat(result.toCanonicalString()).isEqualTo("from-sysprop");
            } finally {
                System.clearProperty(key);
            }
        }

        @Test
        @DisplayName("should use punit environment as fallback")
        void shouldUsePunitEnvironmentAsFallback() {
            var key = "punit.test.custom.covariate";
            
            var context = DefaultCovariateResolutionContext.builder()
                .punitEnvironment(Map.of(key, "from-punit-env"))
                .build();
            
            var resolver = new CustomCovariateResolver(key);
            var result = resolver.resolve(context);
            
            assertThat(result.toCanonicalString()).isEqualTo("from-punit-env");
        }

        @Test
        @DisplayName("should return NOT_SET when not found anywhere")
        void shouldReturnNotSetWhenNotFoundAnywhere() {
            var context = DefaultCovariateResolutionContext.forNow();
            
            var resolver = new CustomCovariateResolver("nonexistent.covariate.key");
            var result = resolver.resolve(context);
            
            assertThat(result.toCanonicalString()).isEqualTo(CovariateProfile.UNDEFINED);
        }
    }

    @Nested
    @DisplayName("toEnvVarName()")
    class ToEnvVarNameTests {

        @Test
        @DisplayName("should uppercase the key")
        void shouldUppercaseTheKey() {
            assertThat(CustomCovariateResolver.toEnvVarName("mykey"))
                .isEqualTo("MYKEY");
        }

        @Test
        @DisplayName("should replace dots with underscores")
        void shouldReplaceDotsWithUnderscores() {
            assertThat(CustomCovariateResolver.toEnvVarName("my.key.name"))
                .isEqualTo("MY_KEY_NAME");
        }

        @Test
        @DisplayName("should replace hyphens with underscores")
        void shouldReplaceHyphensWithUnderscores() {
            assertThat(CustomCovariateResolver.toEnvVarName("my-key-name"))
                .isEqualTo("MY_KEY_NAME");
        }

        @Test
        @DisplayName("should handle mixed separators")
        void shouldHandleMixedSeparators() {
            assertThat(CustomCovariateResolver.toEnvVarName("my.key-name"))
                .isEqualTo("MY_KEY_NAME");
        }
    }

    @Nested
    @DisplayName("getKey()")
    class GetKeyTests {

        @Test
        @DisplayName("should return the covariate key")
        void shouldReturnTheCovariateKey() {
            var resolver = new CustomCovariateResolver("my_custom_covariate");
            
            assertThat(resolver.getKey()).isEqualTo("my_custom_covariate");
        }
    }
}

