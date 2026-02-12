package org.javai.punit.spec.baseline.covariate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.RegionGroupDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RegionResolver")
class RegionResolverTest {

    @Nested
    @DisplayName("Group matching")
    class GroupMatchingTests {

        @Test
        @DisplayName("resolves region in declared group to group label")
        void resolvesRegionInGroupToLabel() {
            var groups = List.of(
                    new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE"),
                    new RegionGroupDefinition(Set.of("GB", "IE"), "UK_IE")
            );
            var resolver = new RegionResolver(groups);
            var context = contextWithRegion("FR");

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("EU_CORE"));
        }

        @Test
        @DisplayName("resolves second group member to same label")
        void resolvesSecondGroupMemberToSameLabel() {
            var groups = List.of(
                    new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE")
            );
            var resolver = new RegionResolver(groups);
            var context = contextWithRegion("DE");

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("EU_CORE"));
        }
    }

    @Nested
    @DisplayName("Remainder")
    class RemainderTests {

        @Test
        @DisplayName("resolves unmatched region to OTHER")
        void resolvesUnmatchedRegionToOther() {
            var groups = List.of(
                    new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE")
            );
            var resolver = new RegionResolver(groups);
            var context = contextWithRegion("US");

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("OTHER"));
        }
    }

    @Nested
    @DisplayName("Undefined")
    class UndefinedTests {

        @Test
        @DisplayName("resolves to UNDEFINED when no region is set")
        void resolvesToUndefinedWhenNoRegionSet() {
            var groups = List.of(
                    new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE")
            );
            var resolver = new RegionResolver(groups);
            var context = contextWithoutRegion();

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("UNDEFINED"));
        }
    }

    @Nested
    @DisplayName("Case insensitivity")
    class CaseInsensitivityTests {

        @Test
        @DisplayName("matches region case-insensitively via group definition")
        void matchesRegionCaseInsensitively() {
            var groups = List.of(
                    new RegionGroupDefinition(Set.of("FR", "DE"), "EU_CORE")
            );
            var resolver = new RegionResolver(groups);
            var context = contextWithRegion("fr");

            var result = resolver.resolve(context);
            assertThat(result).isEqualTo(new CovariateValue.StringValue("EU_CORE"));
        }
    }

    private static CovariateResolutionContext contextWithRegion(String region) {
        return DefaultCovariateResolutionContext.builder()
                .punitEnvironment(Map.of("region", region))
                .build();
    }

    private static CovariateResolutionContext contextWithoutRegion() {
        return DefaultCovariateResolutionContext.builder().build();
    }
}
