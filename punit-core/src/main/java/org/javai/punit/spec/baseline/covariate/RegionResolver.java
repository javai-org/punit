package org.javai.punit.spec.baseline.covariate;

import java.util.List;
import java.util.Objects;
import org.javai.punit.model.CovariateValue;
import org.javai.punit.model.RegionGroupDefinition;

/**
 * Resolves the region covariate to a partition label.
 *
 * <p>Resolves the raw region from (in order):
 * <ol>
 *   <li>System property: {@code punit.region}</li>
 *   <li>Environment variable: {@code PUNIT_REGION}</li>
 *   <li>PUnit environment map: {@code region}</li>
 * </ol>
 *
 * <p>If a region is found, matches it against declared groups and returns
 * the group label. Unmatched regions resolve to "OTHER". If no region is
 * set, resolves to "UNDEFINED".
 */
public final class RegionResolver implements CovariateResolver {

    /** System property key for region. */
    public static final String SYSTEM_PROPERTY_KEY = "punit.region";

    /** Environment variable key for region. */
    public static final String ENV_VAR_KEY = "PUNIT_REGION";

    /** PUnit environment key for region. */
    public static final String PUNIT_ENV_KEY = "region";

    static final String REMAINDER_LABEL = "OTHER";
    static final String UNDEFINED_LABEL = "UNDEFINED";

    private final List<RegionGroupDefinition> groups;

    public RegionResolver(List<RegionGroupDefinition> groups) {
        this.groups = Objects.requireNonNull(groups, "groups must not be null");
    }

    @Override
    public CovariateValue resolve(CovariateResolutionContext context) {
        var rawRegion = context.getSystemProperty(SYSTEM_PROPERTY_KEY)
                .or(() -> context.getEnvironmentVariable(ENV_VAR_KEY))
                .or(() -> context.getPunitEnvironment(PUNIT_ENV_KEY))
                .orElse(null);

        if (rawRegion == null) {
            return new CovariateValue.StringValue(UNDEFINED_LABEL);
        }

        for (RegionGroupDefinition group : groups) {
            if (group.contains(rawRegion)) {
                return new CovariateValue.StringValue(group.label());
            }
        }

        return new CovariateValue.StringValue(REMAINDER_LABEL);
    }
}
