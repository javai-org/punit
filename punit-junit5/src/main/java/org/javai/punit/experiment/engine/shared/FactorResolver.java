package org.javai.punit.experiment.engine.shared;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Resolves factor information from annotations and method parameters.
 *
 * <p>Used by both MEASURE and EXPLORE modes for factor handling.
 */
public final class FactorResolver {

    private FactorResolver() {
        // Utility class
    }

    /**
     * Resolves factor arguments from the @FactorSource annotation.
     *
     * <p>This overload does not search the use case class for simple names.
     * Prefer {@link #resolveFactorArguments(Method, FactorSource, Class)} when
     * a use case class is available.
     *
     * @param testMethod the test method
     * @param factorSource the @FactorSource annotation
     * @return list of factor arguments
     */
    public static List<FactorArguments> resolveFactorArguments(
            Method testMethod, FactorSource factorSource) {
        return resolveFactorArguments(testMethod, factorSource, null);
    }

    /**
     * Resolves factor arguments from the @FactorSource annotation.
     *
     * <p>Resolution order depends on the reference format:
     * <ul>
     *   <li><b>Simple name</b> (e.g., {@code "myFactors"}): Search current class,
     *       then use case class</li>
     *   <li><b>Class#method</b> (e.g., {@code "MyClass#myFactors"}): Search current
     *       package, then use case's package</li>
     *   <li><b>Fully qualified</b> (e.g., {@code "com.example.MyClass#myFactors"}):
     *       Direct lookup</li>
     * </ul>
     *
     * @param testMethod the test method
     * @param factorSource the @FactorSource annotation
     * @param useCaseClass the use case class to search (may be null)
     * @return list of factor arguments
     */
    @SuppressWarnings("unchecked")
    public static List<FactorArguments> resolveFactorArguments(
            Method testMethod, FactorSource factorSource, Class<?> useCaseClass) {

        String sourceReference = factorSource.value();
        Class<?> currentClass = testMethod.getDeclaringClass();

        try {
            Stream<FactorArguments> factorStream;

            if (!sourceReference.contains("#")) {
                // Simple name - search current class, then use case class
                factorStream = resolveSimpleName(sourceReference, currentClass, useCaseClass);
            } else {
                String[] parts = sourceReference.split("#", 2);
                String classRef = parts[0];
                String methodName = parts[1];

                if (classRef.contains(".")) {
                    // Fully qualified - direct lookup
                    factorStream = resolveFullyQualified(classRef, methodName);
                } else {
                    // Class#method - search current package, then use case's package
                    factorStream = resolveClassMethod(classRef, methodName, currentClass, useCaseClass);
                }
            }

            return factorStream.toList();

        } catch (ExtensionConfigurationException e) {
            throw e; // Re-throw with detailed message
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Cannot invoke @FactorSource method '" + sourceReference + "': " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a simple method name by searching current class, then use case class.
     */
    private static Stream<FactorArguments> resolveSimpleName(
            String methodName, Class<?> currentClass, Class<?> useCaseClass) throws Exception {

        // 1. Try current class
        Method method = findMethodInClass(methodName, currentClass);
        if (method != null) {
            return invokeFactorSource(method, methodName);
        }

        // 2. Try use case class
        if (useCaseClass != null && useCaseClass != Void.class && useCaseClass != currentClass) {
            method = findMethodInClass(methodName, useCaseClass);
            if (method != null) {
                return invokeFactorSource(method, methodName);
            }
        }

        // Not found - build helpful error message
        StringBuilder searched = new StringBuilder();
        searched.append("\n  - ").append(currentClass.getName());
        if (useCaseClass != null && useCaseClass != Void.class && useCaseClass != currentClass) {
            searched.append("\n  - ").append(useCaseClass.getName());
        }

        throw new ExtensionConfigurationException(
                "Cannot find @FactorSource method '" + methodName + "'.\n" +
                        "Searched in:" + searched +
                        "\nHint: Use 'ClassName#methodName' or fully qualified 'pkg.ClassName#methodName' for explicit resolution.");
    }

    /**
     * Resolves Class#method by searching current package, then use case's package.
     */
    private static Stream<FactorArguments> resolveClassMethod(
            String className, String methodName, Class<?> currentClass, Class<?> useCaseClass) throws Exception {

        List<String> searchedLocations = new ArrayList<>();

        // 1. Try current class's package
        String currentPackage = currentClass.getPackageName();
        Class<?> targetClass = tryLoadClass(currentPackage + "." + className);
        if (targetClass != null) {
            Method method = findMethodInClass(methodName, targetClass);
            if (method != null) {
                return invokeFactorSource(method, className + "#" + methodName);
            }
        }
        searchedLocations.add(currentPackage + "." + className);

        // 2. Try use case class's package (if different)
        if (useCaseClass != null && useCaseClass != Void.class) {
            String useCasePackage = useCaseClass.getPackageName();
            if (!useCasePackage.equals(currentPackage)) {
                targetClass = tryLoadClass(useCasePackage + "." + className);
                if (targetClass != null) {
                    Method method = findMethodInClass(methodName, targetClass);
                    if (method != null) {
                        return invokeFactorSource(method, className + "#" + methodName);
                    }
                }
                searchedLocations.add(useCasePackage + "." + className);
            }
        }

        // Not found - build helpful error message
        StringBuilder searched = new StringBuilder();
        for (String loc : searchedLocations) {
            searched.append("\n  - ").append(loc);
        }

        throw new ExtensionConfigurationException(
                "Cannot find @FactorSource '" + className + "#" + methodName + "'.\n" +
                        "Searched in:" + searched +
                        "\nHint: Use fully qualified 'pkg.ClassName#methodName' for explicit resolution.");
    }

    /**
     * Resolves a fully qualified class#method reference.
     */
    private static Stream<FactorArguments> resolveFullyQualified(
            String fullyQualifiedClassName, String methodName) throws Exception {

        Class<?> targetClass;
        try {
            targetClass = Class.forName(fullyQualifiedClassName);
        } catch (ClassNotFoundException e) {
            throw new ExtensionConfigurationException(
                    "Cannot find class '" + fullyQualifiedClassName + "' for @FactorSource.");
        }

        Method method = findMethodInClass(methodName, targetClass);
        if (method == null) {
            throw new ExtensionConfigurationException(
                    "Cannot find method '" + methodName + "' in class " + fullyQualifiedClassName);
        }

        return invokeFactorSource(method, fullyQualifiedClassName + "#" + methodName);
    }

    /**
     * Attempts to find a method in a class, returning null if not found.
     */
    private static Method findMethodInClass(String methodName, Class<?> clazz) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Attempts to load a class, returning null if not found.
     */
    private static Class<?> tryLoadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<FactorArguments> invokeFactorSource(Method sourceMethod, String sourceReference)
            throws Exception {
        sourceMethod.setAccessible(true);
        Object result = sourceMethod.invoke(null);

        if (result instanceof Stream) {
            return (Stream<FactorArguments>) result;
        } else if (result instanceof Collection) {
            return ((Collection<FactorArguments>) result).stream();
        } else {
            throw new ExtensionConfigurationException(
                    "Factor source method must return Stream or Collection: " + sourceReference);
        }
    }

    /**
     * Extracts factor info from FactorArguments and method parameters.
     *
     * @param testMethod the test method
     * @param firstArgs the first FactorArguments (for extracting names)
     * @return list of factor info
     */
    public static List<FactorInfo> extractFactorInfosFromArguments(
            Method testMethod, FactorArguments firstArgs) {

        List<FactorInfo> infos = new ArrayList<>();

        // Try to get names from FactorArguments
        String[] argNames = firstArgs.names();
        if (argNames != null) {
            for (int i = 0; i < argNames.length; i++) {
                infos.add(new FactorInfo(i, argNames[i], argNames[i], Object.class));
            }
        } else {
            // Fall back to @Factor annotations on method parameters
            int factorIndex = 0;
            for (Parameter param : testMethod.getParameters()) {
                Factor factor = param.getAnnotation(Factor.class);
                if (factor != null) {
                    infos.add(new FactorInfo(factorIndex++, factor.value(), factor.value(), param.getType()));
                }
            }
        }

        return infos;
    }

    /**
     * Extracts factor info from @FactorSource annotation and arguments.
     *
     * <p>Priority:
     * <ol>
     *   <li>Names embedded in {@link FactorArguments} (best DX - names with values)</li>
     *   <li>{@code @FactorSource(factors = {...})} annotation</li>
     *   <li>{@code @Factor} annotations on method parameters</li>
     * </ol>
     */
    public static List<FactorInfo> extractFactorInfos(
            Method method, FactorSource factorSource, List<FactorArguments> argsList) {

        // 1. Prefer names embedded in FactorArguments
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
                factorInfos.add(new FactorInfo(i, factorNames[i], factorNames[i], Object.class));
            }
            return factorInfos;
        }

        // 3. Fall back to @Factor annotations on method parameters
        return getFactorInfosFromParameters(method);
    }

    /**
     * Extracts factor infos from @Factor-annotated method parameters.
     */
    public static List<FactorInfo> getFactorInfosFromParameters(Method method) {
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
     * Extracts factor values in the order matching factorInfos.
     */
    public static Object[] extractFactorValues(FactorArguments args, List<FactorInfo> factorInfos) {
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
     * Builds a configuration name from factor values for file naming.
     */
    public static String buildConfigName(List<FactorInfo> factorInfos, Object[] values) {
        StringBuilder sb = new StringBuilder();

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
            if (!sb.isEmpty()) sb.append("_");
            String valueStr = formatFactorValue(values[i]);
            sb.append("f").append(i).append("-").append(valueStr);
        }

        return sb.toString();
    }

    /**
     * Formats a factor value for use in file names.
     */
    public static String formatFactorValue(Object value) {
        if (value == null) return "null";
        String str = value.toString();
        return str.replace(" ", "_")
                .replace("/", "-")
                .replace("\\", "-")
                .replace(":", "-")
                .replaceAll("[^a-zA-Z0-9._-]", "");
    }

    /**
     * Resolves a class from a simple or fully qualified name.
     */
    public static Class<?> resolveClass(String className, Class<?> contextClass) {
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

        // 3. Try sibling packages
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPackage = packageName.substring(0, lastDot);

            for (String sibling : new String[]{"usecase", "model", "domain", "service", "api", "core"}) {
                try {
                    return Class.forName(parentPackage + "." + sibling + "." + className);
                } catch (ClassNotFoundException ignored) {
                }
            }

            try {
                return Class.forName(parentPackage + "." + className);
            } catch (ClassNotFoundException ignored) {
            }
        }

        throw new ExtensionConfigurationException(
                "Cannot resolve class '" + className + "' from context " + contextClass.getName() +
                        ". Try using the fully qualified class name.");
    }
}
