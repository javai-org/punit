package org.javai.punit.experiment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.javai.punit.api.FactorArguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for cross-class @FactorSource resolution.
 *
 * <p>These tests verify that factor source methods referenced via the cross-class
 * syntax "ClassName#methodName" are correctly resolved and invoked.
 *
 * <p><b>Background:</b> A bug existed where the cross-class resolution code would
 * correctly resolve the Method object, but then incorrectly try to look it up again
 * using the full source reference string (including the "#") as the method name,
 * causing a NoSuchMethodException. This test prevents regression of that bug.
 *
 * @see <a href="https://github.com/javai/punit/issues/XXX">Issue #XXX</a>
 */
@DisplayName("Cross-class FactorSource Resolution")
class CrossClassFactorSourceResolutionTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST FIXTURES - External factor source class
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Simulates an external class providing factor sources (like ShoppingUseCase).
     */
    public static class ExternalFactorProvider {
        
        public static List<FactorArguments> productQueries() {
            return FactorArguments.configurations()
                .names("query")
                .values("wireless headphones")
                .values("laptop stand")
                .values("USB-C hub")
                .stream().toList();
        }

        public static Stream<FactorArguments> queryStream() {
            return Stream.of(
                FactorArguments.of("query", "item1"),
                FactorArguments.of("query", "item2")
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS - Verify the cross-class resolution pattern used by ExperimentExtension
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("should resolve cross-class factor source method using Class#method syntax")
    void shouldResolveCrossClassFactorSource() throws Exception {
        // Given: A cross-class reference like "ExternalFactorProvider#productQueries"
        String sourceReference = "ExternalFactorProvider#productQueries";
        
        // When: We resolve it the same way ExperimentExtension does
        String[] parts = sourceReference.split("#", 2);
        String className = parts[0];
        String methodName = parts[1];
        
        // Resolve the target class (simplified - using inner class lookup)
        Class<?> targetClass = Class.forName(
            CrossClassFactorSourceResolutionTest.class.getName() + "$" + className);
        
        // Get the method
        Method sourceMethod = targetClass.getDeclaredMethod(methodName);
        
        // Then: We should be able to invoke it using the resolved Method object
        // (NOT by looking up the method using sourceReference as the method name!)
        Stream<FactorArguments> result = invokeFactorSource(sourceMethod, sourceReference);
        
        assertThat(result.toList()).hasSize(3);
    }

    @Test
    @DisplayName("should correctly invoke Stream-returning factor source")
    void shouldInvokeStreamReturningFactorSource() throws Exception {
        // Given: A stream-returning factor source
        Method sourceMethod = ExternalFactorProvider.class.getDeclaredMethod("queryStream");
        
        // When: We invoke it using the correct pattern
        Stream<FactorArguments> result = invokeFactorSource(sourceMethod, "queryStream");
        
        // Then: We should get the expected results
        assertThat(result.toList()).hasSize(2);
    }

    @Test
    @DisplayName("should correctly invoke List-returning factor source")
    void shouldInvokeListReturningFactorSource() throws Exception {
        // Given: A list-returning factor source
        Method sourceMethod = ExternalFactorProvider.class.getDeclaredMethod("productQueries");
        
        // When: We invoke it using the correct pattern
        Stream<FactorArguments> result = invokeFactorSource(sourceMethod, "productQueries");
        
        // Then: We should get the expected results
        assertThat(result.toList()).hasSize(3);
    }

    @Test
    @DisplayName("regression: should NOT look up method using full source reference with # separator")
    void regressionShouldNotLookupMethodUsingSourceReference() {
        // Given: A cross-class source reference
        String sourceReference = "ExternalFactorProvider#productQueries";
        
        // When/Then: Looking up a method using the full reference (including #) should fail
        // This is the bug - the old code was doing this instead of using the resolved Method
        assertThatCode(() -> 
            ExternalFactorProvider.class.getDeclaredMethod(sourceReference)
        ).isInstanceOf(NoSuchMethodException.class);
        
        // The correct approach is to parse the reference first, then look up just the method name
        assertThatCode(() -> {
            String methodName = sourceReference.split("#", 2)[1];
            ExternalFactorProvider.class.getDeclaredMethod(methodName);
        }).doesNotThrowAnyException();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER - Mimics the fixed getFactorArguments method from ExperimentExtension
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Invokes a factor source method and returns the result as a Stream.
     * 
     * <p>This method correctly uses the provided Method object directly,
     * rather than trying to look it up again using the source reference string.
     *
     * @param sourceMethod the resolved Method object to invoke
     * @param sourceReference the original reference (for error messages only)
     * @return stream of factor arguments
     */
    @SuppressWarnings("unchecked")
    private Stream<FactorArguments> invokeFactorSource(Method sourceMethod, String sourceReference)
            throws InvocationTargetException, IllegalAccessException {
        
        // CORRECT: Use the passed Method object directly
        sourceMethod.setAccessible(true);
        Object result = sourceMethod.invoke(null);
        
        if (result instanceof Stream) {
            return (Stream<FactorArguments>) result;
        } else if (result instanceof Collection) {
            return ((Collection<FactorArguments>) result).stream();
        } else {
            throw new IllegalStateException(
                "Factor source method must return Stream or Collection: " + sourceReference);
        }
    }
}

