package org.javai.punit.experiment.engine.shared;

/**
 * Information about a factor parameter.
 *
 * <p>Used for factor resolution and file naming in MEASURE and EXPLORE modes.
 *
 * @param parameterIndex index of this factor in the parameter list
 * @param name logical name of the factor
 * @param filePrefix prefix to use in output file names
 * @param type the Java type of the factor
 */
public record FactorInfo(
        int parameterIndex,
        String name,
        String filePrefix,
        Class<?> type
) {
}
