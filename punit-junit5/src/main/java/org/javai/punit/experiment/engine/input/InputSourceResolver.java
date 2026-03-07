package org.javai.punit.experiment.engine.input;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import org.javai.punit.api.InputSource;

/**
 * Resolves {@link InputSource} annotations to lists of input values.
 *
 * <p>Supports two source types:
 * <ul>
 *   <li><b>Method source</b> — Invokes a static method that returns {@code Stream<T>},
 *       {@code Iterable<T>}, or {@code T[]}</li>
 *   <li><b>File source</b> — Loads and deserializes JSON or CSV files from the classpath</li>
 * </ul>
 *
 * <h2>File Format Detection</h2>
 * <p>File format is determined by extension:
 * <ul>
 *   <li>{@code .json} — JSON array, each element deserialized to the target type</li>
 *   <li>{@code .csv} — CSV with headers matching record component names</li>
 * </ul>
 */
public class InputSourceResolver {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final CsvMapper CSV_MAPPER;

    static {
        CSV_MAPPER = new CsvMapper();
        // Enable reading into records by using field visibility
        CSV_MAPPER.findAndRegisterModules();
    }

    /**
     * Resolves an {@link InputSource} annotation to a list of input values.
     *
     * @param annotation the InputSource annotation
     * @param testClass the test class containing the method source (if applicable)
     * @param inputType the target type for deserialization
     * @return list of input values
     * @throws InputSourceException if resolution fails
     */
    public List<Object> resolve(InputSource annotation, Class<?> testClass, Class<?> inputType) {
        validateAnnotation(annotation);

        if (!annotation.value().isEmpty()) {
            return resolveMethodSource(annotation.value(), testClass);
        } else {
            return resolveFileSource(annotation.file(), inputType);
        }
    }

    private void validateAnnotation(InputSource annotation) {
        boolean hasMethodSource = !annotation.value().isEmpty();
        boolean hasFileSource = !annotation.file().isEmpty();

        if (!hasMethodSource && !hasFileSource) {
            throw new InputSourceException("@InputSource requires either value() or file() to be specified");
        }

        if (hasMethodSource && hasFileSource) {
            throw new InputSourceException("@InputSource cannot specify both value() and file()");
        }
    }

    // ========== Method Source Resolution ==========

    private List<Object> resolveMethodSource(String methodName, Class<?> testClass) {
        Method method = findMethod(methodName, testClass);
        validateMethodSource(method);

        try {
            Object result = method.invoke(null);
            return toList(result);
        } catch (ReflectiveOperationException e) {
            throw new InputSourceException("Failed to invoke method source: " + methodName, e);
        }
    }

    private Method findMethod(String methodName, Class<?> testClass) {
        // Search in the test class and its superclasses
        Class<?> current = testClass;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }

        throw new InputSourceException("Method source not found: " + methodName + " in " + testClass.getName());
    }

    private void validateMethodSource(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new InputSourceException("Method source must be static: " + method.getName());
        }

        Class<?> returnType = method.getReturnType();
        if (!Stream.class.isAssignableFrom(returnType) &&
            !Iterable.class.isAssignableFrom(returnType) &&
            !returnType.isArray()) {
            throw new InputSourceException(
                    "Method source must return Stream<T>, Iterable<T>, or T[]: " + method.getName() +
                    " returns " + returnType.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> toList(Object result) {
        if (result instanceof Stream<?> stream) {
            return (List<Object>) stream.toList();
        } else if (result instanceof Iterable<?> iterable) {
            return (List<Object>) StreamSupport.stream(iterable.spliterator(), false).toList();
        } else if (result.getClass().isArray()) {
            return new ArrayList<>(Arrays.asList((Object[]) result));
        }
        throw new InputSourceException("Unexpected method source return type: " + result.getClass().getName());
    }

    // ========== File Source Resolution ==========

    private List<Object> resolveFileSource(String path, Class<?> inputType) {
        String extension = getFileExtension(path);

        return switch (extension.toLowerCase()) {
            case "json" -> loadJson(path, inputType);
            case "csv" -> loadCsv(path, inputType);
            default -> throw new InputSourceException(
                    "Unsupported file format: " + extension + ". Supported: .json, .csv");
        };
    }

    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0 || lastDot == path.length() - 1) {
            throw new InputSourceException("File path must have an extension: " + path);
        }
        return path.substring(lastDot + 1);
    }

    private List<Object> loadJson(String path, Class<?> inputType) {
        try (InputStream is = getResourceAsStream(path)) {
            JavaType listType = JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, inputType);
            return JSON_MAPPER.readValue(is, listType);
        } catch (IOException e) {
            throw new InputSourceException("Failed to load JSON file: " + path, e);
        }
    }

    private List<Object> loadCsv(String path, Class<?> inputType) {
        try (InputStream is = getResourceAsStream(path)) {
            // Use empty schema with header - let Jackson infer columns from CSV header row
            // and map to fields by name
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            MappingIterator<?> iterator = CSV_MAPPER
                    .readerFor(inputType)
                    .with(schema)
                    .readValues(is);
            return new ArrayList<>(iterator.readAll());
        } catch (IOException e) {
            throw new InputSourceException("Failed to load CSV file: " + path, e);
        }
    }

    private InputStream getResourceAsStream(String path) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new InputSourceException("Resource not found on classpath: " + path);
        }
        return is;
    }
}
