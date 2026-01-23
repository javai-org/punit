package org.javai.punit.spec.baseline.covariate;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.punit.api.CovariateSource;
import org.javai.punit.api.FactorAnnotations;
import org.javai.punit.model.CovariateDeclaration;
import org.javai.punit.model.CovariateProfile;
import org.javai.punit.model.CovariateValue;

/**
 * Resolves a complete covariate profile from a declaration and context.
 *
 * <p>This is the high-level entry point for resolving all declared covariates.
 *
 * <h2>Resolution Hierarchy</h2>
 * <ol>
 *   <li>{@code @CovariateSource} method on use case instance (if available)</li>
 *   <li>System property: {@code org.javai.punit.covariate.<key>}</li>
 *   <li>Environment variable: {@code ORG_JAVAI_PUNIT_COVARIATE_<KEY>}</li>
 *   <li>Default resolver (from registry)</li>
 * </ol>
 */
public final class CovariateProfileResolver {

    private static final String SYS_PROP_PREFIX = "org.javai.punit.covariate.";
    private static final String ENV_VAR_PREFIX = "ORG_JAVAI_PUNIT_COVARIATE_";

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

        // Build map of @CovariateSource methods from use case instance
        Map<String, Method> sourceMethods = discoverCovariateSourceMethods(context);

        var builder = CovariateProfile.builder();
        
        for (String key : declaration.allKeys()) {
            var value = resolveValue(key, context, sourceMethods);
            builder.put(key, value);
        }

        return builder.build();
    }

    private Map<String, Method> discoverCovariateSourceMethods(CovariateResolutionContext context) {
        Map<String, Method> methods = new HashMap<>();

        Optional<Object> instanceOpt = context.getUseCaseInstance();
        if (instanceOpt.isEmpty()) {
            return methods;
        }

        Object instance = instanceOpt.get();
        for (Method method : instance.getClass().getMethods()) {
            CovariateSource annotation = method.getAnnotation(CovariateSource.class);
            if (annotation != null) {
                String key = FactorAnnotations.resolveCovariateSourceKey(method, annotation);
                methods.put(key, method);
            }
        }

        return methods;
    }

    private CovariateValue resolveValue(
            String key, 
            CovariateResolutionContext context,
            Map<String, Method> sourceMethods) {
        
        // 1. Try @CovariateSource method
        Method sourceMethod = sourceMethods.get(key);
        if (sourceMethod != null) {
            var instanceOpt = context.getUseCaseInstance();
            if (instanceOpt.isPresent()) {
                try {
                    Object result = sourceMethod.invoke(instanceOpt.get());
                    if (result != null) {
                        return toCovariateValue(result);
                    }
                } catch (Exception e) {
                    // Fall through to other resolution methods
                }
            }
        }

        // 2. Try system property
        String sysPropKey = SYS_PROP_PREFIX + key;
        Optional<String> sysPropValue = context.getSystemProperty(sysPropKey);
        if (sysPropValue.isPresent()) {
            return new CovariateValue.StringValue(sysPropValue.get());
        }

        // 3. Try environment variable
        String envVarKey = ENV_VAR_PREFIX + key.toUpperCase().replace('-', '_');
        Optional<String> envVarValue = context.getEnvironmentVariable(envVarKey);
        if (envVarValue.isPresent()) {
            return new CovariateValue.StringValue(envVarValue.get());
        }

        // 4. Fall back to registry resolver
        var resolver = registry.getResolver(key);
        return resolver.resolve(context);
    }

    private CovariateValue toCovariateValue(Object result) {
        if (result instanceof CovariateValue cv) {
            return cv;
        }
        if (result instanceof String s) {
            return new CovariateValue.StringValue(s);
        }
        return new CovariateValue.StringValue(result.toString());
    }
}

