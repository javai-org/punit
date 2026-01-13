package org.javai.punit.engine.covariate;

import java.util.Objects;

import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.CovariateProfile;

/**
 * Resolves a complete covariate profile from a declaration and context.
 *
 * <p>This is the high-level entry point for resolving all declared covariates.
 */
public final class CovariateProfileResolver {

    private final CovariateResolverRegistry registry;

    /**
     * Creates a resolver with standard resolvers.
     */
    public CovariateProfileResolver() {
        this(CovariateResolverRegistry.withStandardResolvers());
    }

    /**
     * Creates a resolver with a custom registry.
     *
     * @param registry the resolver registry
     */
    public CovariateProfileResolver(CovariateResolverRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Resolves all declared covariates to create a profile.
     *
     * @param declaration the covariate declaration
     * @param context the resolution context
     * @return the resolved covariate profile
     */
    public CovariateProfile resolve(CovariateDeclaration declaration, CovariateResolutionContext context) {
        Objects.requireNonNull(declaration, "declaration must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (declaration.isEmpty()) {
            return CovariateProfile.empty();
        }

        var builder = CovariateProfile.builder();
        
        for (String key : declaration.allKeys()) {
            var resolver = registry.getResolver(key);
            var value = resolver.resolve(context);
            builder.put(key, value);
        }

        return builder.build();
    }
}

