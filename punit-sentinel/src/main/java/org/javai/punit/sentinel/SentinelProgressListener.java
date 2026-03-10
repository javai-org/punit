package org.javai.punit.sentinel;

/**
 * Receives progress notifications during sentinel execution.
 *
 * <p>Used by the CLI's {@code --verbose} flag to show real-time progress
 * for both test and experiment execution.
 */
interface SentinelProgressListener {

    /**
     * Called when a test or experiment method begins execution.
     *
     * @param testName the test or experiment method name
     * @param totalSamples the planned number of samples
     */
    void onMethodStart(String testName, int totalSamples);

    /**
     * Called after each sample completes.
     *
     * @param sampleNumber the 1-based sample number
     * @param totalSamples the planned number of samples
     * @param passed whether this sample passed
     */
    void onSampleComplete(int sampleNumber, int totalSamples, boolean passed);

    /**
     * Called when a probabilistic test method finishes with a verdict.
     *
     * @param testName the test method name
     * @param passed whether the test passed
     */
    void onTestComplete(String testName, boolean passed);

    /**
     * Called when an experiment method finishes data collection.
     *
     * @param experimentName the experiment method name
     * @param samples total samples executed
     * @param successes number of successful samples
     */
    void onExperimentComplete(String experimentName, int samples, int successes);
}
