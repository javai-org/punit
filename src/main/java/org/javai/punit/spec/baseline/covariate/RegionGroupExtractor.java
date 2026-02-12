package org.javai.punit.spec.baseline.covariate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.javai.punit.api.RegionGroup;
import org.javai.punit.model.RegionGroupDefinition;

/**
 * Extracts {@link RegionGroup} annotations into {@link RegionGroupDefinition} instances.
 *
 * <p>Validates ISO 3166-1 alpha-2 country codes, normalizes to uppercase,
 * and enforces mutual exclusivity (no region code in more than one group).
 */
final class RegionGroupExtractor {

    static final Set<String> VALID_ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    /**
     * Extracts region group definitions from the given annotations.
     *
     * @param regionGroups the region group annotations
     * @return the extracted definitions (empty list if input is empty)
     * @throws CovariateValidationException if codes are invalid or a region appears in more than one group
     */
    List<RegionGroupDefinition> extract(RegionGroup[] regionGroups) {
        if (regionGroups.length == 0) {
            return List.of();
        }

        var seenRegions = new HashSet<String>();
        var result = new ArrayList<RegionGroupDefinition>();

        for (RegionGroup group : regionGroups) {
            var regions = Arrays.stream(group.value())
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            if (regions.isEmpty()) {
                throw new CovariateValidationException("Region group must contain at least one region code");
            }

            for (String region : regions) {
                if (!VALID_ISO_COUNTRIES.contains(region)) {
                    throw new CovariateValidationException(
                            "Invalid ISO 3166-1 alpha-2 country code: '" + region + "'");
                }
                if (!seenRegions.add(region)) {
                    throw new CovariateValidationException(
                            "Region '" + region + "' appears in more than one @RegionGroup (mutual exclusivity violated)");
                }
            }

            String label = group.label().isEmpty() ? deriveRegionGroupLabel(regions) : group.label();
            result.add(new RegionGroupDefinition(regions, label));
        }

        return result;
    }

    static String deriveRegionGroupLabel(Set<String> regions) {
        return regions.stream()
                .sorted()
                .collect(Collectors.joining("_"));
    }
}
