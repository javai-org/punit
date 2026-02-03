package org.javai.punit.examples.usecases;

/**
 * Example input data record for tests and experiments.
 *
 * <p>This record demonstrates loading structured input from a JSON file
 * via {@code @InputSource}. The fields can be whatever the test/experiment
 * needs - the framework is agnostic about the shape.
 *
 * @param instruction the natural language instruction
 * @param expected the expected JSON response
 */
public record InputData(String instruction, String expected) {
}
