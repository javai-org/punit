package org.javai.punit.experiment.engine;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.javai.punit.experiment.api.Experiment;
import org.javai.punit.experiment.api.ExperimentContext;
import org.javai.punit.experiment.api.UseCaseContext;
import org.javai.punit.experiment.model.DefaultUseCaseContext;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.UseCaseResult;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * JUnit 5 extension that drives experiment execution.
 *
 * <p>This extension:
 * <ul>
 *   <li>Discovers use cases from the test class</li>
 *   <li>Executes use cases repeatedly as samples</li>
 *   <li>Aggregates results</li>
 *   <li>Generates empirical baselines</li>
 * </ul>
 */
public class ExperimentExtension implements TestTemplateInvocationContextProvider, InvocationInterceptor {
    
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
        
        String useCaseId = annotation.useCase();
        int samples = annotation.samples();
        
        // Store annotation and metadata for later - use case discovery happens in interceptor
        // because test instance isn't available yet in provideTestTemplateInvocationContexts
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put("annotation", annotation);
        store.put("useCaseId", useCaseId);
        store.put("testMethod", testMethod);
        
        // Create aggregator
        ExperimentResultAggregator aggregator = new ExperimentResultAggregator(useCaseId, samples);
        store.put("aggregator", aggregator);
        
        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger currentSample = new AtomicInteger(0);
        store.put("terminated", terminated);
        store.put("currentSample", currentSample);
        store.put("startTimeMs", System.currentTimeMillis());
        store.put("useCaseResolved", new AtomicBoolean(false));
        
        // Generate sample stream
        return Stream.iterate(1, i -> i + 1)
            .limit(samples)
            .takeWhile(i -> !terminated.get())
            .map(i -> new ExperimentInvocationContext(i, samples, useCaseId));
    }
    
    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        
        // Use parent context's store since provideTestTemplateInvocationContexts stored data there
        ExtensionContext parentContext = extensionContext.getParent().orElse(extensionContext);
        ExtensionContext.Store store = parentContext.getStore(NAMESPACE);
        
        // Resolve use case on first invocation (when test instance is available)
        AtomicBoolean useCaseResolved = store.get("useCaseResolved", AtomicBoolean.class);
        if (useCaseResolved != null && !useCaseResolved.get()) {
            resolveUseCaseOnFirstInvocation(extensionContext, store);
            useCaseResolved.set(true);
        }
        
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        UseCaseDefinition useCase = store.get("useCase", UseCaseDefinition.class);
        UseCaseContext useCaseContext = store.get("useCaseContext", UseCaseContext.class);
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
                checkAndGenerateBaseline(extensionContext, store);
                return;
            }
        }
        
        // Check token budget
        if (annotation.tokenBudget() > 0 && aggregator.getTotalTokens() >= annotation.tokenBudget()) {
            terminated.set(true);
            aggregator.setTerminated("TOKEN_BUDGET_EXHAUSTED",
                "Token budget of " + annotation.tokenBudget() + " exceeded");
            invocation.skip();
            checkAndGenerateBaseline(extensionContext, store);
            return;
        }
        
        // Execute use case
        try {
            // Extract input arguments from context parameters
            Object[] inputArgs = extractInputArguments(useCase, useCaseContext);
            UseCaseResult result = useCase.invoke(useCaseContext, inputArgs);
            
            // Determine success/failure based on result values
            // Default: look for common success indicators
            boolean success = determineSuccess(result);
            
            if (success) {
                aggregator.recordSuccess(result);
            } else {
                String failureCategory = determineFailureCategory(result);
                aggregator.recordFailure(result, failureCategory);
            }
            
        } catch (Exception e) {
            aggregator.recordException(e);
        }
        
        // Report progress
        reportProgress(extensionContext, aggregator, sample, annotation.samples());
        
        // Check if this is the last sample
        if (sample >= annotation.samples() || terminated.get()) {
            if (!terminated.get()) {
                aggregator.setCompleted();
            }
            generateBaseline(extensionContext, store);
        }
        
        // Skip the actual test method body - experiment execution is use-case driven
        invocation.skip();
    }
    
    private UseCaseRegistry getOrCreateRegistry(ExtensionContext context) {
        ExtensionContext.Store store = context.getRoot().getStore(NAMESPACE);
        return store.getOrComputeIfAbsent("registry", k -> new UseCaseRegistry(), UseCaseRegistry.class);
    }
    
    /**
     * Resolves the use case on the first invocation when the test instance is available.
     *
     * <p>This is necessary because {@code provideTestTemplateInvocationContexts} is called
     * before the test instance is created by JUnit.
     */
    private void resolveUseCaseOnFirstInvocation(ExtensionContext context, ExtensionContext.Store store) {
        String useCaseId = store.get("useCaseId", String.class);
        Method testMethod = store.get("testMethod", Method.class);
        
        UseCaseRegistry registry = getOrCreateRegistry(context);
        
        // Now the test instance should be available - register use cases from it
        context.getTestInstance().ifPresent(registry::registerAll);
        
        UseCaseDefinition useCase = registry.resolve(useCaseId)
            .orElseThrow(() -> new ExtensionConfigurationException(
                "Use case not found: " + useCaseId + ". " +
                "Ensure a method annotated with @UseCase(\"" + useCaseId + "\") exists in the test class or its superclasses."));
        
        // Build context from @ExperimentContext annotation
        UseCaseContext useCaseContext = buildContext(testMethod);
        
        store.put("useCase", useCase);
        store.put("useCaseContext", useCaseContext);
    }
    
    private UseCaseContext buildContext(Method method) {
        DefaultUseCaseContext.Builder builder = DefaultUseCaseContext.builder();
        
        ExperimentContext contextAnnotation = method.getAnnotation(ExperimentContext.class);
        if (contextAnnotation != null) {
            builder.backend(contextAnnotation.backend());
            
            // Parse parameters
            String[] params = contextAnnotation.parameters().length > 0 
                ? contextAnnotation.parameters() 
                : contextAnnotation.template();
            
            for (String param : params) {
                int eqIdx = param.indexOf('=');
                if (eqIdx > 0) {
                    String key = param.substring(0, eqIdx).trim();
                    String value = param.substring(eqIdx + 1).trim();
                    builder.parameter(key, parseValue(value));
                }
            }
        }
        
        return builder.build();
    }
    
    private Object parseValue(String value) {
        // Try to parse as number or boolean
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }
    
    /**
     * Extracts input arguments for the use case method from context parameters.
     *
     * <p>This method examines the use case method's parameters and attempts to
     * match them with context parameters by name. Parameters of type
     * {@link UseCaseContext} are skipped (they are handled separately).
     *
     * <p>Parameter matching is done by name when available (with -parameters compiler flag),
     * and falls back to positional matching when names are not preserved.
     *
     * @param useCase the use case definition
     * @param context the use case context containing parameters
     * @return an array of input arguments for the use case method
     */
    private Object[] extractInputArguments(UseCaseDefinition useCase, UseCaseContext context) {
        Method method = useCase.method();
        Parameter[] parameters = method.getParameters();
        List<Object> inputArgs = new ArrayList<>();
        Map<String, Object> contextParams = context.getAllParameters();
        
        for (Parameter param : parameters) {
            // Skip UseCaseContext parameters - they're handled separately
            if (UseCaseContext.class.isAssignableFrom(param.getType())) {
                continue;
            }
            
            String paramName = param.getName();
            Class<?> paramType = param.getType();
            
            // Try to find a matching parameter in the context by name
            Object value = contextParams.get(paramName);
            
            // If name-based lookup failed and parameter name is synthetic (arg0, arg1, etc.),
            // try positional matching based on non-context parameter order
            if (value == null && paramName.startsWith("arg")) {
                // Fall back: use first string parameter for String type, etc.
                value = findMatchingParameter(paramType, contextParams);
            }
            
            if (value != null) {
                // Convert value to the expected type if necessary
                inputArgs.add(convertValue(value, paramType));
            } else {
                // Use null or default values for missing parameters
                inputArgs.add(getDefaultValue(paramType));
            }
        }
        
        return inputArgs.toArray();
    }
    
    /**
     * Finds a parameter value matching the target type when name-based lookup fails.
     */
    private Object findMatchingParameter(Class<?> targetType, Map<String, Object> params) {
        for (Object value : params.values()) {
            if (value != null && targetType.isInstance(value)) {
                return value;
            }
            // Try converting string values
            if (value instanceof String && targetType == String.class) {
                return value;
            }
        }
        return null;
    }
    
    /**
     * Converts a value to the target type.
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultValue(targetType);
        }
        
        // Already the right type
        if (targetType.isInstance(value)) {
            return value;
        }
        
        String stringValue = value.toString();
        
        // Handle primitive types and their wrappers
        if (targetType == String.class) {
            return stringValue;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(stringValue);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(stringValue);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(stringValue);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(stringValue);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(stringValue);
        }
        
        // Default: return the value as-is and hope for the best
        return value;
    }
    
    /**
     * Returns the default value for a type.
     */
    private Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0;
        }
        return null;
    }
    
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
    
    private void reportProgress(ExtensionContext context, ExperimentResultAggregator aggregator, 
                               int currentSample, int totalSamples) {
        // Report via JUnit TestReporter if available
        context.publishReportEntry("punit.mode", "EXPERIMENT");
        context.publishReportEntry("punit.sample", currentSample + "/" + totalSamples);
        context.publishReportEntry("punit.successRate", 
            String.format("%.2f%%", aggregator.getObservedSuccessRate() * 100));
    }
    
    private void checkAndGenerateBaseline(ExtensionContext context, ExtensionContext.Store store) {
        ExperimentResultAggregator aggregator = store.get("aggregator", ExperimentResultAggregator.class);
        if (aggregator.getSamplesExecuted() > 0) {
            generateBaseline(context, store);
        }
    }
    
    private void generateBaseline(ExtensionContext context, ExtensionContext.Store store) {
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
        
        // Write baseline to file
        try {
            Path outputPath = resolveBaselineOutputPath(annotation, aggregator.getUseCaseId());
            BaselineWriter writer = new BaselineWriter();
            writer.write(baseline, outputPath, annotation.outputFormat());
            
            context.publishReportEntry("punit.baseline.outputPath", outputPath.toString());
        } catch (IOException e) {
            context.publishReportEntry("punit.baseline.error", e.getMessage());
        }
        
        // Publish final report
        publishFinalReport(context, aggregator);
    }
    
    private Path resolveBaselineOutputPath(Experiment annotation, String useCaseId) {
        String baseDir = annotation.baselineOutputDir();
        String filename = useCaseId.replace('.', '-') + "." + annotation.outputFormat();
        
        // Try to resolve relative to test resources
        Path path = Paths.get("src", "test", "resources", baseDir, filename);
        return path;
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
	 * Invocation context for a single experiment sample.
	 */
	private record ExperimentInvocationContext(int sampleNumber, int totalSamples,
											   String useCaseId) implements TestTemplateInvocationContext {

		@Override
		public String getDisplayName(int invocationIndex) {
			return String.format("[%s] sample %d/%d", useCaseId, sampleNumber, totalSamples);
		}

		@Override
		public List<Extension> getAdditionalExtensions() {
			return Collections.emptyList();
		}
	}
}

