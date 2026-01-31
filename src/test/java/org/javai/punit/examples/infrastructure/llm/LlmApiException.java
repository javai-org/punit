package org.javai.punit.examples.infrastructure.llm;

/**
 * Exception thrown when an LLM API call fails.
 *
 * <p>This exception captures details about the API failure to aid debugging:
 * <ul>
 *   <li>HTTP status code (if available)</li>
 *   <li>Response body (truncated for display)</li>
 *   <li>Helper methods to classify the error type</li>
 * </ul>
 *
 * <h2>Error Classification</h2>
 * <p>Use the helper methods to determine the error type:
 * <ul>
 *   <li>{@link #isRateLimited()} - 429 Too Many Requests</li>
 *   <li>{@link #isAuthError()} - 401 Unauthorized or 403 Forbidden</li>
 *   <li>{@link #isServerError()} - 5xx errors</li>
 *   <li>{@link #isTransient()} - Errors that may succeed on retry</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try {
 *     String response = llm.chat(system, user, model, temp);
 * } catch (LlmApiException e) {
 *     if (e.isRateLimited()) {
 *         // Wait and retry
 *     } else if (e.isAuthError()) {
 *         // Check API key configuration
 *     }
 * }
 * }</pre>
 */
public class LlmApiException extends RuntimeException {

    private static final int MAX_BODY_LENGTH = 200;

    private final int statusCode;
    private final String responseBody;

    /**
     * Creates a new API exception with status code and response details.
     *
     * @param message a description of what went wrong
     * @param statusCode the HTTP status code (or -1 if not applicable)
     * @param responseBody the response body from the API
     */
    public LlmApiException(String message, int statusCode, String responseBody) {
        super(formatMessage(message, statusCode, responseBody));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Creates a new API exception from a failure message (no HTTP details).
     *
     * @param message the failure message
     */
    public LlmApiException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * Creates a new API exception with a cause.
     *
     * @param message a description of what went wrong
     * @param cause the underlying exception
     */
    public LlmApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    /**
     * Returns the HTTP status code, or -1 if not applicable.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the full response body from the API, or null if not available.
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Returns true if this is a rate limit error (HTTP 429).
     */
    public boolean isRateLimited() {
        return statusCode == 429;
    }

    /**
     * Returns true if this is an authentication error (HTTP 401 or 403).
     */
    public boolean isAuthError() {
        return statusCode == 401 || statusCode == 403;
    }

    /**
     * Returns true if this is a server error (HTTP 5xx).
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * Returns true if this error is transient and may succeed on retry.
     *
     * <p>Transient errors include:
     * <ul>
     *   <li>Rate limiting (429)</li>
     *   <li>Server errors (5xx)</li>
     *   <li>Network timeouts (no status code)</li>
     * </ul>
     */
    public boolean isTransient() {
        return isRateLimited() || isServerError() || statusCode == -1;
    }

    private static String formatMessage(String message, int statusCode, String responseBody) {
        if (statusCode <= 0) {
            return message;
        }
        String truncatedBody = truncate(responseBody, MAX_BODY_LENGTH);
        return "%s [HTTP %d]: %s".formatted(message, statusCode, truncatedBody);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "<no body>";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
