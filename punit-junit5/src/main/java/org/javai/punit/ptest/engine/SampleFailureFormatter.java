package org.javai.punit.ptest.engine;

/**
 * Formats exception messages for individual sample failures in probabilistic tests.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Creating verdict hints that show current test progress</li>
 *   <li>Extracting concise failure reasons from exceptions</li>
 *   <li>Combining hints and reasons for IDE display</li>
 * </ul>
 *
 * <p>The formatted messages help users understand that a sample failure doesn't
 * necessarily mean the overall test failed—the statistical verdict is determined
 * at the end based on pass rate.
 *
 * <p>Package-private: internal implementation detail of the test extension.
 */
class SampleFailureFormatter {

    /**
     * Formats a complete sample failure message for IDE display.
     *
     * <p>The message includes:
     * <ul>
     *   <li>A verdict hint showing current progress (samples executed, successes, threshold)</li>
     *   <li>The concise failure reason extracted from the exception</li>
     * </ul>
     *
     * @param failure the exception that caused the sample to fail
     * @param successes number of successful samples so far
     * @param executed number of samples executed so far
     * @param planned total number of planned samples
     * @param threshold the minimum pass rate threshold (0.0 to 1.0)
     * @return the formatted failure message
     */
    String formatSampleFailure(Throwable failure, int successes, int executed, int planned, double threshold) {
        String hint = formatVerdictHint(successes, executed, planned, threshold);
        String reason = extractFailureReason(failure);
        return hint + "\n" + reason;
    }

    /**
     * Formats a status hint to include in exception messages.
     *
     * <p>Shows current stats without claiming a verdict (which isn't known until
     * test completes). This helps users understand that:
     * <ul>
     *   <li>The sample failure is being recorded</li>
     *   <li>The test may still pass if enough other samples succeed</li>
     *   <li>The final verdict will appear in the console summary</li>
     * </ul>
     *
     * @param successes number of successful samples so far
     * @param executed number of samples executed so far
     * @param planned total number of planned samples
     * @param threshold the minimum pass rate threshold (0.0 to 1.0)
     * @return the formatted verdict hint
     */
    String formatVerdictHint(int successes, int executed, int planned, double threshold) {
        return String.format(
                "[PUnit sample %d/%d: %d successes so far, need %s - see console for final verdict]",
                executed, planned, successes, org.javai.punit.reporting.RateFormat.format(threshold));
    }

    /**
     * Extracts a concise failure reason from an exception.
     *
     * <p>This method provides just the essential message without verbose stack traces.
     * It handles common patterns in assertion library messages:
     * <ul>
     *   <li>Null or blank messages → returns exception class name</li>
     *   <li>Multi-line messages (e.g., AssertJ) → returns first non-blank line</li>
     *   <li>Messages starting with newline → skips to first content line</li>
     * </ul>
     *
     * @param failure the exception to extract the reason from
     * @return a concise failure reason string
     */
    String extractFailureReason(Throwable failure) {
        if (failure == null) {
            return "Unknown failure";
        }

        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }

        // Trim and take first line only (AssertJ messages can be multi-line)
        String firstLine = message.lines().findFirst().orElse(message).trim();

        // If the first line is empty (AssertJ often starts with newline), take next non-empty
        if (firstLine.isEmpty()) {
            firstLine = message.lines()
                    .filter(line -> !line.isBlank())
                    .findFirst()
                    .orElse(failure.getClass().getSimpleName())
                    .trim();
        }

        return firstLine;
    }
}

