package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.api.FactorValues;
import org.javai.punit.api.UseCaseProvider;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ExperimentMode;
import org.javai.punit.experiment.api.Factor;
import org.javai.punit.experiment.api.FactorArguments;
import org.javai.punit.experiment.api.FactorSource;
import org.javai.punit.experiment.api.ResultCaptor;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * JUnit 5 extension that drives experiment execution.
 *
 * <p>This extension:
 * <ul>
 *   <li>Injects {@link ResultCaptor} into experiment methods</li>
 *   <li>Executes experiment methods repeatedly as samples</li>
 *   <li>Aggregates results from the captor</li>
 *   <li>Generates empirical baselines</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>The new architecture uses a method-execution model:
 * <ol>
 *   <li>The experiment method receives a {@link ResultCaptor} parameter</li>
 *   <li>The method executes and calls the use case</li>
 *   <li>The method records the result via {@code captor.record(result)}</li>
 *   <li>The extension aggregates results after each method execution</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Experiment(useCase = ShoppingUseCase.class, samples = 1000)
 * void measureSearchBaseline(ShoppingUseCase useCase, ResultCaptor captor) {
 *     captor.record(useCase.searchProducts("headphones", context));
 * }
 * }</pre>
 */
public class ExperimentExtension implements TestTemplateInvocationContextProvider, 
        InvocationInterceptor {
    
    private static final ExtensionContext.Namespace NAMESPACE = 
        ExtensionContext.Namespace.create(ExperimentExtension.class);
    
    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
            .map(m -> m.isAnnotationPresent(Experiment.class))
            .orElse(false);
    }
    
    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod();
        Experiment annotation = testMethod.getAnnotation(Experiment.class);
        
        // Validate samples/samplesPerConfig mutual exclusivity
        validateSampleConfiguration(annotation, testMethod);
        
        // Resolve use case ID from class reference or legacy string
        String useCaseId = resolveUseCaseId(annotation);
        
        // Store annotation and metadata for later
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put("annotation", annotation);
        store.put("useCaseId", useCaseId);
        store.put("useCaseClass", annotation.useCase());
        store.put("testMethod", testMethod);
        store.put("startTimeMs", System.currentTimeMillis());
        store.put("contextBuilt", new AtomicBoolean(false));
        
        // Dispatch based on experiment mode
        if (annotation.mode() == ExperimentMode.EXPLORE) {
            return provideExploreInvocationContexts(context, testMethod, annotation, useCaseId, store);
        } else {
            return provideMeasureInvocationContexts(annotation, useCaseId, store);
        }
    }
    
    /**
     * Validates that samples and samplesPerConfig are not both specified.
     *
     * <p>These attributes are mutually exclusive:
     * <ul>
     *   <li>{@code samples}: Total number of samples (for MEASURE mode)</li>
     *   <li>{@code samplesPerConfig}: Samples per factor configuration (for EXPLORE mode)</li>
     * </ul>
     *
     * @param annotation the experiment annotation
     * @param testMethod the test method (for error messages)
     * @throws ExtensionConfigurationException if both are specified
     */
    private void validateSampleConfiguration(Experiment annotation, Method testMethod) {
        boolean hasSamples = annotation.samples() > 0;
        boolean hasSamplesPerConfig = annotation.samplesPerConfig() > 0;
        
        if (hasSamples && hasSamplesPerConfig) {
            throw new ExtensionConfigurationException(
                "Experiment method '" + testMethod.getName() + "' specifies both 'samples' and 'samplesPerConfig'. " +
                "These attributes are mutually exclusive:\n" +
                "  - Use 'samples' for MEASURE mode (total sample count)\n" +
                "  - Use 'samplesPerConfig' for EXPLORE mode (samples per factor configuration)\n" +
                "Remove one of these attributes to resolve the conflict.");
        }
    }
    
    /**
     * Provides invocation contexts for MEASURE mode (single configuration, many samples).
     *
     * <p>Supports optional @FactorSource for cycling through representative inputs.
     * With samples=1000 and a factor source with 10 entries, each factor is used ~100 times.
     */
    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideMeasureInvocationContexts(
            Experiment annotation, String useCaseId, ExtensionContext.Store store) {
        
        int samples = annotation.mode().getEffectiveSampleSize(annotation.samples());
        
        // Create single aggregator
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samples);
        store.put("aggregator", aggregator);
        
        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger currentSample = new AtomicInteger(0);
        store.put("terminated", terminated);
        store.put("currentSample", currentSample);
        store.put("mode", ExperimentMode.MEASURE);
        
        // Check for @FactorSource annotation - if present, use cycling factors
        Method testMethod = store.get("testMethod", Method.class);
        FactorSource factorSource = testMethod != null ? testMethod.getAnnotation(FactorSource.class) : null;
        
        if (factorSource != null && testMethod != null) {
            // MEASURE mode with factor source - use cycling
            return provideMeasureWithFactorsInvocationContexts(
                testMethod, factorSource, samples, useCaseId, store, aggregator, terminated);
        }
        
        // No factor source - simple sample stream
        return Stream.iterate(1, i -> i + 1)
            .limit(samples)
            .takeWhile(i -> !terminated.get())
            .map(i -> new MeasureInvocationContext(i, samples, useCaseId, new ResultCaptor()));
    }
    
    /**
     * Provides invocation contexts for MEASURE mode with factor source (cycling).
     */
    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideMeasureWithFactorsInvocationContexts(
            Method testMethod, FactorSource factorSource, int samples, String useCaseId,
            ExtensionContext.Store store, ExperimentResultAggregator aggregator, AtomicBoolean terminated) {
        
        // Resolve the factor source
        String sourceReference = factorSource.value();
        Stream<FactorArguments> factorStream;
        try {
            if (sourceReference.contains("#")) {
                // Cross-class reference
                String[] parts = sourceReference.split("#", 2);
                String className = parts[0];
                String methodName = parts[1];
                Class<?> targetClass = resolveClass(className, testMethod.getDeclaringClass());
                Method sourceMethod = targetClass.getDeclaredMethod(methodName);
                sourceMethod.setAccessible(true);
                Object result = sourceMethod.invoke(null);
                if (result instanceof Stream) {
                    factorStream = (Stream<FactorArguments>) result;
                } else if (result instanceof java.util.Collection) {
                    factorStream = ((java.util.Collection<FactorArguments>) result).stream();
                } else {
                    throw new ExtensionConfigurationException(
                        "Factor source method must return Stream or Collection: " + sourceReference);
                }
            } else {
                // Same-class reference
                Method sourceMethod = testMethod.getDeclaringClass().getDeclaredMethod(sourceReference);
                sourceMethod.setAccessible(true);
                Object result = sourceMethod.invoke(null);
                if (result instanceof Stream) {
                    factorStream = (Stream<FactorArguments>) result;
                } else if (result instanceof java.util.Collection) {
                    factorStream = ((java.util.Collection<FactorArguments>) result).stream();
                } else {
                    throw new ExtensionConfigurationException(
                        "Factor source method must return Stream or Collection: " + sourceReference);
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            throw new ExtensionConfigurationException(
                "Cannot invoke @FactorSource method '" + sourceReference + "': " + e.getMessage(), e);
        }
        
        // Materialize factors for cycling
        List<FactorArguments> factorsList = factorStream.toList();
        if (factorsList.isEmpty()) {
            throw new ExtensionConfigurationException(
                "Factor source '" + sourceReference + "' returned no factors");
        }
        
        // Extract factor names from the first FactorArguments
        List<FactorInfo> factorInfos = extractFactorInfosFromArguments(testMethod, factorsList.get(0));
        store.put("factorInfos", factorInfos);
        
        // Generate sample stream with cycling factors
        return Stream.iterate(1, i -> i + 1)
            .limit(samples)
            .takeWhile(i -> !terminated.get())
            .map(i -> {
                // Cycling: factor index = (sample - 1) % factorCount
                int factorIndex = (i - 1) % factorsList.size();
                FactorArguments args = factorsList.get(factorIndex);
                Object[] factorValues = extractFactorValues(args, factorInfos);
                return new MeasureWithFactorsInvocationContext(
                    i, samples, useCaseId, new ResultCaptor(), factorValues, factorInfos);
            });
    }
    
    /**
     * Extracts factor info from FactorArguments and method parameters.
     */
    private List<FactorInfo> extractFactorInfosFromArguments(Method testMethod, FactorArguments firstArgs) {
        List<FactorInfo> infos = new ArrayList<>();
        
        // Try to get names from FactorArguments
        String[] argNames = firstArgs.names();
        if (argNames != null) {
            for (int i = 0; i < argNames.length; i++) {
                // FactorInfo(parameterIndex, name, filePrefix, type)
                infos.add(new FactorInfo(i, argNames[i], argNames[i], Object.class));
            }
        } else {
            // Fall back to @Factor annotations on method parameters
            int factorIndex = 0;
            for (java.lang.reflect.Parameter param : testMethod.getParameters()) {
                Factor factor = param.getAnnotation(Factor.class);
                if (factor != null) {
                    infos.add(new FactorInfo(factorIndex++, factor.value(), factor.value(), param.getType()));
                }
            }
        }
        
        return infos;
    }
    
    /**
     * Extracts factor values in the order matching factorInfos.
     */
    private Object[] extractFactorValues(FactorArguments args, List<FactorInfo> factorInfos) {
        Object[] values = new Object[factorInfos.size()];
        String[] argNames = args.names();
        
        for (int i = 0; i < factorInfos.size(); i++) {
            FactorInfo info = factorInfos.get(i);
            if (argNames != null) {
                // Find by name
                for (int j = 0; j < argNames.length; j++) {
                    if (argNames[j].equals(info.name())) {
                        values[i] = args.get(j);
                        break;
                    }
                }
            } else {
                // Use positional index
                values[i] = args.get(info.parameterIndex());
            }
        }
        
        return values;
    }
    
    /**
     * Resolves a class from a simple or fully qualified name.
     * Tries sibling packages (e.g., from .experiment to .usecase) for convenience.
     */
    private Class<?> resolveClass(String className, Class<?> contextClass) {
        String packageName = contextClass.getPackageName();
        
        // 1. Try same package
        try {
            return Class.forName(packageName + "." + className);
        } catch (ClassNotFoundException ignored) {
        }
        
        // 2. Try fully qualified name
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
        }
        
        // 3. Try sibling packages (e.g., from .experiment to .usecase)
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPackage = packageName.substring(0, lastDot);
            
            for (String sibling : new String[]{"usecase", "model", "domain", "service", "api", "core"}) {
                try {
                    return Class.forName(parentPackage + "." + sibling + "." + className);
                } catch (ClassNotFoundException ignored) {
                }
            }
            
            // Try parent package directly
            try {
                return Class.forName(parentPackage + "." + className);
            } catch (ClassNotFoundException ignored) {
            }
        }
        
        throw new ExtensionConfigurationException(
            "Cannot resolve class '" + className + "' from context " + contextClass.getName() +
            ". Try using the fully qualified class name.");
    }
    
    /**
     * Provides invocation contexts for EXPLORE mode (multiple configurations from factors).
     */
    @SuppressWarnings("unchecked")
    private Stream<TestTemplateInvocationContext> provideExploreInvocationContexts(
            ExtensionContext context, Method testMethod, Experiment annotation, 
            String useCaseId, ExtensionContext.Store store) {
        
        int samplesPerConfig = annotation.mode().getEffectiveSampleSize(annotation.samplesPerConfig());
        
        // Find @FactorSource annotation
        FactorSource factorSource = testMethod.getAnnotation(FactorSource.class);
        if (factorSource == null) {
            // Warn: EXPLORE without factors is equivalent to MEASURE
            context.publishReportEntry("punit.warning", 
                "EXPLORE without @FactorSource is equivalent to MEASURE. " +
                "Consider adding @FactorSource or using mode = MEASURE.");
            
            // Fall back to baseline-like behavior with samplesPerConfig
            store.put("mode", ExperimentMode.EXPLORE);
            ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samplesPerConfig);
            store.put("aggregator", aggregator);
            store.put("terminated", new AtomicBoolean(false));
            store.put("currentSample", new AtomicInteger(0));
            
            return Stream.iterate(1, i -> i + 1)
                .limit(samplesPerConfig)
                .map(i -> new MeasureInvocationContext(i, samplesPerConfig, useCaseId, new ResultCaptor()));
        }
        
        // Find factor method
        String methodName = factorSource.value();
        Stream<FactorArguments> factorCombinations;
        try {
            Method sourceMethod = testMethod.getDeclaringClass().getDeclaredMethod(methodName);
            sourceMethod.setAccessible(true);
            factorCombinations = (Stream<FactorArguments>) sourceMethod.invoke(null);
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                "Cannot invoke @FactorSource method '" + methodName + "': " + e.getMessage(), e);
        }
        
        // Collect all configurations first
        List<FactorArguments> argsList = factorCombinations.toList();
        
        // Get factor names: from FactorArguments, @FactorSource.factors(), or @Factor annotations
        List<FactorInfo> factorInfos = extractFactorInfos(testMethod, factorSource, argsList);
        
        // Store explore-mode metadata
        store.put("mode", ExperimentMode.EXPLORE);
        store.put("factorInfos", factorInfos);
        store.put("configAggregators", new LinkedHashMap<String, ExperimentResultAggregator>());
        store.put("terminated", new AtomicBoolean(false));
        
        // Generate invocation contexts for all configs × samples
        List<ExploreInvocationContext> invocations = new ArrayList<>();
        
        for (int configIndex = 0; configIndex < argsList.size(); configIndex++) {
            FactorArguments args = argsList.get(configIndex);
            Object[] factorValues = args.get();
            
            // Build configuration name from factor values
            String configName = buildConfigName(factorInfos, factorValues);
            
            // Create aggregator for this configuration
            ExperimentResultAggregator configAggregator = 
                new ExperimentResultAggregator(useCaseId + "/" + configName, samplesPerConfig);
            ((Map<String, ExperimentResultAggregator>) store.get("configAggregators", Map.class))
                .put(configName, configAggregator);
            
            // Generate samples for this configuration
            for (int sample = 1; sample <= samplesPerConfig; sample++) {
                invocations.add(new ExploreInvocationContext(
                    sample, samplesPerConfig, configIndex + 1, argsList.size(),
                    useCaseId, configName, factorValues, factorInfos, new ResultCaptor()
                ));
            }
        }
        
        return invocations.stream().map(c -> (TestTemplateInvocationContext) c);
    }
    
    /**
     * Extracts factor information for file naming.
     *
     * <p>Priority:
     * <ol>
     *   <li>Names embedded in {@link FactorArguments} (best DX - names with values)</li>
     *   <li>{@code @FactorSource(factors = {...})} annotation</li>
     *   <li>{@code @Factor} annotations on method parameters</li>
     * </ol>
     */
    private List<FactorInfo> extractFactorInfos(Method method, FactorSource factorSource, 
                                                 List<FactorArguments> argsList) {
        // 1. Prefer names embedded in FactorArguments (best DX)
        if (!argsList.isEmpty() && argsList.get(0).hasNames()) {
            String[] names = argsList.get(0).names();
            List<FactorInfo> factorInfos = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                factorInfos.add(new FactorInfo(i, names[i], names[i], Object.class));
            }
            return factorInfos;
        }
        
        // 2. Factor names from @FactorSource annotation
        String[] factorNames = factorSource.factors();
        if (factorNames.length > 0) {
            List<FactorInfo> factorInfos = new ArrayList<>();
            for (int i = 0; i < factorNames.length; i++) {
                String name = factorNames[i];
                factorInfos.add(new FactorInfo(i, name, name, Object.class));
            }
            return factorInfos;
        }
        
        // 3. Fall back to @Factor annotations on method parameters
        List<FactorInfo> factorInfos = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            Factor factor = parameters[i].getAnnotation(Factor.class);
            if (factor != null) {
                String name = factor.value();
                String filePrefix = factor.filePrefix().isEmpty() ? name : factor.filePrefix();
                factorInfos.add(new FactorInfo(i, name, filePrefix, parameters[i].getType()));
            }
        }
        
        return factorInfos;
    }
    
    /**
     * Builds a configuration name from factor values for file naming.
     *
     * <p>Assumes @Factor-annotated parameters are at the start of the method
     * parameter list and in the same order as FactorArguments values.
     */
    private String buildConfigName(List<FactorInfo> factorInfos, Object[] values) {
        StringBuilder sb = new StringBuilder();
        
        // Use whichever is smaller - factorInfos or values
        int count = Math.min(factorInfos.size(), values.length);
        
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append("_");
            FactorInfo info = factorInfos.get(i);
            Object value = values[i];
            String valueStr = formatFactorValue(value);
            sb.append(info.filePrefix()).append("-").append(valueStr);
        }
        
        // If there are more values than factorInfos, add generic names
        for (int i = count; i < values.length; i++) {
            if (sb.length() > 0) sb.append("_");
            String valueStr = formatFactorValue(values[i]);
            sb.append("f").append(i).append("-").append(valueStr);
        }
        
        return sb.toString();
    }
    
    /**
     * Formats a factor value for use in file names.
     */
    private String formatFactorValue(Object value) {
        if (value == null) return "null";
        String str = value.toString();
        // Clean up for file system compatibility
        return str.replace(" ", "_")
                  .replace("/", "-")
                  .replace("\\", "-")
                  .replace(":", "-")
                  .replaceAll("[^a-zA-Z0-9._-]", "");
    }
    
    /**
     * Info about a factor parameter.
     */
    record FactorInfo(int parameterIndex, String name, String filePrefix, Class<?> type) {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INVOCATION INTERCEPTOR - Executes experiment and captures results
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        
        // Use parent context's store since provideTestTemplateInvocationContexts stored data there
        ExtensionContext parentContext = extensionContext.getParent().orElse(extensionContext);
        ExtensionContext.Store store = parentContext.getStore(NAMESPACE);
        
        ExperimentMode mode = store.get("mode", ExperimentMode.class);
        if (mode == null) mode = ExperimentMode.MEASURE;
        
        if (mode == ExperimentMode.EXPLORE) {
            interceptExploreMethod(invocation, invocationContext, extensionContext, store);
        } else {
            interceptMeasureMethod(invocation, extensionContext, store);
        }
    }
    
    /**
     * Intercepts MEASURE mode experiment execution.
     */
    @SuppressWarnings("unchecked")
    private void interceptMeasureMethod(
            Invocation<Void> invocation,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {
        
        // Build context on first invocation
        AtomicBoolean contextBuilt = store.get("contextBuilt", AtomicBoolean.class);
        if (contextBuilt != null && !contextBuilt.get()) {
            Method testMethod = store.get("testMethod", Method.class);
            UseCaseContext useCaseContext = buildContext(testMethod);
            store.put("useCaseContext", useCaseContext);
            contextBuilt.set(true);
        }
        
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        Experiment annotation = store.get("annotation", Experiment.class);
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);
        AtomicInteger currentSample = store.get("currentSample", AtomicInteger.class);
        Long startTimeMs = store.get("startTimeMs", Long.class);
        
        int sample = currentSample.incrementAndGet();
        
        // Check time budget
        if (annotation.timeBudgetMs() > 0) {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            if (elapsed >= annotation.timeBudgetMs()) {
                terminated.set(true);
                aggregator.setTerminated("TIME_BUDGET_EXHAUSTED", 
                    "Time budget of " + annotation.timeBudgetMs() + "ms exceeded");
                invocation.skip();
                checkAndGenerateSpec(extensionContext, store);
                return;
            }
        }
        
        // Check token budget
        if (annotation.tokenBudget() > 0 && aggregator.getTotalTokens() >= annotation.tokenBudget()) {
            terminated.set(true);
            aggregator.setTerminated("TOKEN_BUDGET_EXHAUSTED",
                "Token budget of " + annotation.tokenBudget() + " exceeded");
            invocation.skip();
            checkAndGenerateSpec(extensionContext, store);
            return;
        }
        
        // Get captor from the current invocation context's store
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        ResultCaptor captor = invocationStore.get("captor", ResultCaptor.class);
        
        try {
            invocation.proceed();
            recordResult(captor, aggregator);
        } catch (Throwable e) {
            aggregator.recordException(e);
        }
        
        // Report progress
        reportProgress(extensionContext, aggregator, sample, annotation.mode().getEffectiveSampleSize(annotation.samples()));
        
        // Check if this is the last sample
        if (sample >= annotation.samples() || terminated.get()) {
            if (!terminated.get()) {
                aggregator.setCompleted();
            }
            generateSpec(extensionContext, store);
        }
    }
    
    /**
     * Intercepts EXPLORE mode experiment execution.
     */
    @SuppressWarnings("unchecked")
    private void interceptExploreMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext,
            ExtensionContext.Store store) throws Throwable {
        
        Experiment annotation = store.get("annotation", Experiment.class);
        AtomicBoolean terminated = store.get("terminated", AtomicBoolean.class);
        Map<String, ExperimentResultAggregator> configAggregators = 
            store.get("configAggregators", Map.class);
        List<FactorInfo> factorInfos = store.get("factorInfos", List.class);
        
        // Get explore invocation context info from extension context store
        ExtensionContext.Store invocationStore = extensionContext.getStore(NAMESPACE);
        ResultCaptor captor = invocationStore.get("captor", ResultCaptor.class);
        String configName = invocationStore.get("configName", String.class);
        int sampleInConfig = invocationStore.get("sampleInConfig", Integer.class);
        int samplesPerConfig = annotation.mode().getEffectiveSampleSize(annotation.samplesPerConfig());
        Object[] factorValues = invocationStore.get("factorValues", Object[].class);
        
        ExperimentResultAggregator aggregator = configAggregators.get(configName);
        if (aggregator == null) {
            throw new ExtensionConfigurationException(
                "No aggregator found for configuration: " + configName);
        }
        
        // Set factor values on the UseCaseProvider BEFORE the method executes
        UseCaseProvider provider = findUseCaseProvider(extensionContext);
        if (provider != null && factorValues != null) {
            List<String> factorNames = factorInfos.stream()
                .map(FactorInfo::name)
                .toList();
            provider.setCurrentFactorValues(factorValues, factorNames);
        }
        
        try {
            invocation.proceed();
            recordResult(captor, aggregator);
        } catch (Throwable e) {
            aggregator.recordException(e);
        }
        
        // Report progress for this config
        extensionContext.publishReportEntry("punit.mode", "EXPLORE");
        extensionContext.publishReportEntry("punit.config", configName);
        extensionContext.publishReportEntry("punit.sample", sampleInConfig + "/" + samplesPerConfig);
        extensionContext.publishReportEntry("punit.successRate", 
            String.format("%.2f%%", aggregator.getObservedSuccessRate() * 100));
        
        // Check if this is the last sample for this config
        if (sampleInConfig >= samplesPerConfig) {
            aggregator.setCompleted();
            generateExploreSpec(extensionContext, store, configName, aggregator);
        }
    }
    
    /**
     * Records the result from the captor into the aggregator.
     */
    private void recordResult(ResultCaptor captor, ExperimentResultAggregator aggregator) {
        if (captor != null && captor.hasResult()) {
            UseCaseResult result = captor.getResult();
            boolean success = determineSuccess(result);
            
            if (success) {
                aggregator.recordSuccess(result);
            } else {
                String failureCategory = determineFailureCategory(result);
                aggregator.recordFailure(result, failureCategory);
            }
        } else if (captor != null && captor.hasException()) {
            aggregator.recordException(captor.getException());
        } else {
            aggregator.recordSuccess(UseCaseResult.builder().value("recorded", false).build());
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONTEXT BUILDING
    // ═══════════════════════════════════════════════════════════════════════════
    
    @SuppressWarnings("unused")
    private UseCaseContext buildContext(Method method) {
        // Context is now managed by the experiment method body and UseCaseProvider
        // This method returns an empty context for baseline metadata purposes
        return DefaultUseCaseContext.builder().build();
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT DETERMINATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private boolean determineSuccess(UseCaseResult result) {
        // Check for common success indicators (in priority order)
        if (result.hasValue("success")) {
            return result.getBoolean("success", false);
        }
        if (result.hasValue("isSuccess")) {
            return result.getBoolean("isSuccess", false);
        }
        if (result.hasValue("passed")) {
            return result.getBoolean("passed", false);
        }
        if (result.hasValue("isValid")) {
            return result.getBoolean("isValid", false);
        }
        if (result.hasValue("isValidJson")) {
            return result.getBoolean("isValidJson", false);
        }
        if (result.hasValue("error")) {
            return result.getValue("error", Object.class).isEmpty();
        }
        
        // Default to success if no failure indicators
        return true;
    }
    
    private String determineFailureCategory(UseCaseResult result) {
        // Check for common failure category indicators
        if (result.hasValue("failureCategory")) {
            return result.getString("failureCategory", "unknown");
        }
        if (result.hasValue("errorType")) {
            return result.getString("errorType", "unknown");
        }
        if (result.hasValue("errorCode")) {
            return result.getString("errorCode", "unknown");
        }
        return "unknown";
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PROGRESS & BASELINE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private void reportProgress(ExtensionContext context, ExperimentResultAggregator aggregator, 
                               int currentSample, int totalSamples) {
        // Report via JUnit TestReporter if available
        context.publishReportEntry("punit.mode", "EXPERIMENT");
        context.publishReportEntry("punit.sample", currentSample + "/" + totalSamples);
        context.publishReportEntry("punit.successRate", 
            String.format("%.2f%%", aggregator.getObservedSuccessRate() * 100));
    }
    
    private void checkAndGenerateSpec(ExtensionContext context, ExtensionContext.Store store) {
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        if (aggregator.getSamplesExecuted() > 0) {
            generateSpec(context, store);
        }
    }
    
    private void generateSpec(ExtensionContext context, ExtensionContext.Store store) {
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        UseCaseContext useCaseContext = store.get("useCaseContext", UseCaseContext.class);
        Experiment annotation = store.get("annotation", Experiment.class);
        
        EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = generator.generate(
            aggregator,
            context.getTestClass().orElse(null),
            context.getTestMethod().orElse(null),
            useCaseContext
        );
        
        // Write spec to file (in src/test/resources/punit/specs/)
        try {
            Path outputPath = resolveMeasureOutputPath(annotation, aggregator.getUseCaseId());
            BaselineWriter writer = new BaselineWriter();
            writer.write(baseline, outputPath);
            
            context.publishReportEntry("punit.spec.outputPath", outputPath.toString());
        } catch (IOException e) {
            context.publishReportEntry("punit.spec.error", e.getMessage());
        }
        
        // Publish final report
        publishFinalReport(context, aggregator);
    }
    
    /**
     * Generates a spec for a single configuration in EXPLORE mode.
     */
    private void generateExploreSpec(
            ExtensionContext context, 
            ExtensionContext.Store store, 
            String configName,
            ExperimentResultAggregator aggregator) {
        
        UseCaseContext useCaseContext = store.get("useCaseContext", UseCaseContext.class);
        if (useCaseContext == null) {
            useCaseContext = DefaultUseCaseContext.builder().build();
        }
        
        Experiment annotation = store.get("annotation", Experiment.class);
        String useCaseId = store.get("useCaseId", String.class);
        
        EmpiricalBaselineGenerator generator = new EmpiricalBaselineGenerator();
        EmpiricalBaseline baseline = generator.generate(
            aggregator,
            context.getTestClass().orElse(null),
            context.getTestMethod().orElse(null),
            useCaseContext
        );
        
        // Write spec to config-specific file (in explorations/)
        try {
            Path outputPath = resolveExploreOutputPath(annotation, useCaseId, configName);
            BaselineWriter writer = new BaselineWriter();
            writer.write(baseline, outputPath);
            
            context.publishReportEntry("punit.spec.outputPath", outputPath.toString());
            context.publishReportEntry("punit.config.complete", configName);
        } catch (IOException e) {
            context.publishReportEntry("punit.spec.error", 
                "Config " + configName + ": " + e.getMessage());
        }
        
        // Publish report for this config
        context.publishReportEntry("punit.config.successRate", 
            String.format("%s: %.4f", configName, aggregator.getObservedSuccessRate()));
    }
    
    /**
     * Default output directory for MEASURE mode specs.
     */
    private static final String DEFAULT_SPECS_DIR = "src/test/resources/punit/specs";
    
    /**
     * Default output directory for EXPLORE mode specs.
     */
    private static final String DEFAULT_EXPLORATIONS_DIR = "src/test/resources/punit/explorations";
    
    /**
     * Resolves the output path for MEASURE mode (single configuration).
     *
     * <p>Output: {@code src/test/resources/punit/specs/{useCaseId}.yaml}
     */
    private Path resolveMeasureOutputPath(Experiment annotation, String useCaseId) throws IOException {
        String filename = useCaseId.replace('.', '-') + ".yaml";
        
        // Check for system property override (set by Gradle task)
        String outputDirOverride = System.getProperty("punit.specs.outputDir");
        Path baseDir;
        if (outputDirOverride != null && !outputDirOverride.isEmpty()) {
            baseDir = Paths.get(outputDirOverride);
        } else {
            baseDir = Paths.get(DEFAULT_SPECS_DIR);
        }
        
        Files.createDirectories(baseDir);
        return baseDir.resolve(filename);
    }
    
    /**
     * Resolves the output path for EXPLORE mode (multiple configurations).
     *
     * <p>Structure:
     * <pre>
     * src/test/resources/punit/explorations/
     * └── ShoppingUseCase/              # Directory for use case
     *     ├── model-gpt-4_temp-0.0.yaml
     *     └── model-gpt-4_temp-0.7.yaml
     * </pre>
     */
    private Path resolveExploreOutputPath(Experiment annotation, String useCaseId, String configName) 
            throws IOException {
        
        String filename = configName + ".yaml";
        
        // Check for system property override (set by Gradle task)
        String outputDirOverride = System.getProperty("punit.explorations.outputDir");
        Path baseDir;
        if (outputDirOverride != null && !outputDirOverride.isEmpty()) {
            baseDir = Paths.get(outputDirOverride);
        } else {
            baseDir = Paths.get(DEFAULT_EXPLORATIONS_DIR);
        }
        
        // Create subdirectory for use case
        Path useCaseDir = baseDir.resolve(useCaseId.replace('.', '-'));
        Files.createDirectories(useCaseDir);
        
        return useCaseDir.resolve(filename);
    }
    
    private void publishFinalReport(ExtensionContext context, ExperimentResultAggregator aggregator) {
        context.publishReportEntry("punit.experiment.complete", "true");
        context.publishReportEntry("punit.useCaseId", aggregator.getUseCaseId());
        context.publishReportEntry("punit.samplesExecuted", 
            String.valueOf(aggregator.getSamplesExecuted()));
        context.publishReportEntry("punit.successRate", 
            String.format("%.4f", aggregator.getObservedSuccessRate()));
        context.publishReportEntry("punit.standardError",
            String.format("%.4f", aggregator.getStandardError()));
        context.publishReportEntry("punit.terminationReason", 
            aggregator.getTerminationReason());
        context.publishReportEntry("punit.elapsedMs", 
            String.valueOf(aggregator.getElapsedMs()));
        context.publishReportEntry("punit.totalTokens",
            String.valueOf(aggregator.getTotalTokens()));
    }

    /**
     * Resolves the use case ID from the annotation.
     *
     * <p>Priority:
     * <ol>
     *   <li>If {@code useCase} class is specified (not Void.class), use {@link UseCaseProvider#resolveId}</li>
     *   <li>Otherwise, use the legacy {@code useCaseId} string</li>
     * </ol>
     */
    private String resolveUseCaseId(Experiment annotation) {
        Class<?> useCaseClass = annotation.useCase();
        
        // New pattern: use case class reference
        if (useCaseClass != null && useCaseClass != Void.class) {
            return UseCaseProvider.resolveId(useCaseClass);
        }
        
        // Legacy pattern: string ID
        String legacyId = annotation.useCaseId();
        if (legacyId != null && !legacyId.isEmpty()) {
            return legacyId;
        }
        
        throw new ExtensionConfigurationException(
            "Experiment must specify either useCase class or useCaseId. " +
            "Recommended: @Experiment(useCase = MyUseCase.class, ...)");
    }

    /**
     * Invocation context for MEASURE mode (single configuration, no factors).
     */
    private record MeasureInvocationContext(int sampleNumber, int totalSamples,
                                               String useCaseId, ResultCaptor captor) 
            implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return String.format("[%s] sample %d/%d", useCaseId, sampleNumber, totalSamples);
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new CaptorParameterResolver(captor, null, 0));
        }
    }
    
    /**
     * Invocation context for MEASURE mode with factor source (cycling).
     *
     * <p>Provides factor values that cycle through the factor source entries.
     * With samples=1000 and 10 factor entries, each factor is used ~100 times.
     */
    private record MeasureWithFactorsInvocationContext(
            int sampleNumber, int totalSamples,
            String useCaseId, ResultCaptor captor,
            Object[] factorValues, List<FactorInfo> factorInfos) 
            implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return String.format("[%s] sample %d/%d", useCaseId, sampleNumber, totalSamples);
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            List<Extension> extensions = new ArrayList<>();
            extensions.add(new CaptorParameterResolver(captor, null, sampleNumber, factorValues));
            extensions.add(new FactorParameterResolver(factorValues, factorInfos));
            extensions.add(new FactorValuesResolver(factorValues, factorInfos));
            return extensions;
        }
    }
    
    /**
     * Invocation context for EXPLORE mode (multiple configurations).
     */
    private record ExploreInvocationContext(
            int sampleInConfig, int samplesPerConfig,
            int configIndex, int totalConfigs,
            String useCaseId, String configName,
            Object[] factorValues, List<FactorInfo> factorInfos,
            ResultCaptor captor) 
            implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return String.format("[%s] config %d/%d (%s) sample %d/%d", 
                useCaseId, configIndex, totalConfigs, configName, sampleInConfig, samplesPerConfig);
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            List<Extension> extensions = new ArrayList<>();
            // IMPORTANT: FactorValuesInitializer must be first to set factor values 
            // on UseCaseProvider BEFORE any parameter resolution happens
            extensions.add(new FactorValuesInitializer(factorValues, factorInfos));
            extensions.add(new CaptorParameterResolver(captor, configName, sampleInConfig, factorValues));
            extensions.add(new FactorParameterResolver(factorValues, factorInfos));
            extensions.add(new FactorValuesResolver(factorValues, factorInfos));
            return extensions;
        }
    }
    
    /**
     * Initializes factor values on the UseCaseProvider BEFORE parameter resolution
     * and clears them AFTER the test completes.
     *
     * <p>This is critical for auto-wired use case injection: the provider needs to know
     * the current factor values when resolving the use case parameter, which happens
     * before the test method is invoked (and before interceptTestTemplateMethod).
     */
    private static class FactorValuesInitializer implements 
            org.junit.jupiter.api.extension.BeforeEachCallback,
            org.junit.jupiter.api.extension.AfterEachCallback {
        private final Object[] factorValues;
        private final List<FactorInfo> factorInfos;
        
        FactorValuesInitializer(Object[] factorValues, List<FactorInfo> factorInfos) {
            this.factorValues = factorValues;
            this.factorInfos = factorInfos;
        }
        
        @Override
        public void beforeEach(ExtensionContext context) {
            // Find the UseCaseProvider and set factor values
            UseCaseProvider provider = findProvider(context);
            if (provider != null && factorValues != null) {
                List<String> factorNames = factorInfos.stream()
                    .map(FactorInfo::name)
                    .toList();
                provider.setCurrentFactorValues(factorValues, factorNames);
            }
        }
        
        @Override
        public void afterEach(ExtensionContext context) {
            // Clear factor values after the test completes
            UseCaseProvider provider = findProvider(context);
            if (provider != null) {
                provider.clearCurrentFactorValues();
            }
        }
        
        private UseCaseProvider findProvider(ExtensionContext context) {
            Object testInstance = context.getRequiredTestInstance();
            for (java.lang.reflect.Field field : testInstance.getClass().getDeclaredFields()) {
                if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        return (UseCaseProvider) field.get(testInstance);
                    } catch (IllegalAccessException e) {
                        // Continue searching
                    }
                }
            }
            return null;
        }
    }
    
    /**
     * Parameter resolver that provides FactorValues for accessing factor data by name.
     */
    private static class FactorValuesResolver implements ParameterResolver {
        private final Object[] factorValues;
        private final List<FactorInfo> factorInfos;
        
        FactorValuesResolver(Object[] factorValues, List<FactorInfo> factorInfos) {
            this.factorValues = factorValues;
            this.factorInfos = factorInfos;
        }
        
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType() == FactorValues.class;
        }
        
        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            List<String> names = factorInfos.stream()
                .map(FactorInfo::name)
                .toList();
            return new FactorValues(factorValues, names);
        }
    }
    
    /**
     * Parameter resolver that provides the ResultCaptor for the current invocation.
     */
    private static class CaptorParameterResolver implements ParameterResolver {
        private final ResultCaptor captor;
        private final String configName;
        private final int sampleInConfig;
        private final Object[] factorValues;
        
        CaptorParameterResolver(ResultCaptor captor, String configName, int sampleInConfig) {
            this(captor, configName, sampleInConfig, null);
        }
        
        CaptorParameterResolver(ResultCaptor captor, String configName, int sampleInConfig, Object[] factorValues) {
            this.captor = captor;
            this.configName = configName;
            this.sampleInConfig = sampleInConfig;
            this.factorValues = factorValues;
        }
        
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
                throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == ResultCaptor.class;
        }
        
        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
                throws ParameterResolutionException {
            // Store in the extension context so the interceptor can access it
            ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
            store.put("captor", captor);
            if (configName != null) {
                store.put("configName", configName);
                store.put("sampleInConfig", sampleInConfig);
            }
            if (factorValues != null) {
                store.put("factorValues", factorValues);
            }
            return captor;
        }
    }
    
    /**
     * Finds the UseCaseProvider in the test instance.
     */
    private UseCaseProvider findUseCaseProvider(ExtensionContext context) {
        Object testInstance = context.getRequiredTestInstance();
        for (java.lang.reflect.Field field : testInstance.getClass().getDeclaredFields()) {
            if (UseCaseProvider.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    return (UseCaseProvider) field.get(testInstance);
                } catch (IllegalAccessException e) {
                    // Continue searching
                }
            }
        }
        return null;
    }
    
    /**
     * Parameter resolver that provides factor values for EXPLORE mode.
     */
    private static class FactorParameterResolver implements ParameterResolver {
        private final Object[] factorValues;
        private final List<FactorInfo> factorInfos;
        
        FactorParameterResolver(Object[] factorValues, List<FactorInfo> factorInfos) {
            this.factorValues = factorValues;
            this.factorInfos = factorInfos;
        }
        
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
                throws ParameterResolutionException {
            // Check if this parameter has a @Factor annotation
            return parameterContext.getParameter().isAnnotationPresent(Factor.class);
        }
        
        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) 
                throws ParameterResolutionException {
            Factor factor = parameterContext.getParameter().getAnnotation(Factor.class);
            String factorName = factor.value();
            
            // Find the matching factor value
            for (int i = 0; i < factorInfos.size(); i++) {
                if (factorInfos.get(i).name().equals(factorName)) {
                    return factorValues[i];
                }
            }
            
            throw new ParameterResolutionException(
                "No factor value found for @Factor(\"" + factorName + "\")");
        }
    }
}
