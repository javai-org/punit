package org.javai.punit.examples.shopping.usecase;

import java.util.List;
import java.util.Map;

/**
 * Represents a response from an LLM for a shopping query.
 *
 * <p>This class captures both the raw response and parsed data,
 * allowing tests to verify format compliance and content quality.
 */
public class LlmResponse {

    private final String rawJson;
    private final boolean validJson;
    private final int tokensUsed;
    private final String query;
    private final List<Product> products;
    private final Integer totalResults;
    private final Map<String, Boolean> presentFields;

    private LlmResponse(Builder builder) {
        this.rawJson = builder.rawJson;
        this.validJson = builder.validJson;
        this.tokensUsed = builder.tokensUsed;
        this.query = builder.query;
        this.products = builder.products;
        this.totalResults = builder.totalResults;
        this.presentFields = builder.presentFields;
    }

    /**
     * Returns the raw JSON string from the LLM.
     */
    public String rawJson() {
        return rawJson;
    }

    /**
     * Returns whether the response is valid JSON.
     */
    public boolean isValidJson() {
        return validJson;
    }

    /**
     * Returns the number of tokens consumed by this LLM call.
     */
    public int tokensUsed() {
        return tokensUsed;
    }

    /**
     * Returns the original query echoed back (if present).
     */
    public String query() {
        return query;
    }

    /**
     * Returns the list of products in the response.
     */
    public List<Product> products() {
        return products != null ? products : List.of();
    }

    /**
     * Returns the total results count from the response.
     */
    public Integer totalResults() {
        return totalResults;
    }

    /**
     * Checks if a specific field is present in the response.
     *
     * @param fieldName the name of the field to check
     * @return true if the field is present
     */
    public boolean hasField(String fieldName) {
        return presentFields != null && presentFields.getOrDefault(fieldName, false);
    }

    /**
     * Creates a new builder for constructing LlmResponse instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing LlmResponse instances.
     */
    public static class Builder {
        private String rawJson;
        private boolean validJson = true;
        private int tokensUsed;
        private String query;
        private List<Product> products;
        private Integer totalResults;
        private Map<String, Boolean> presentFields;

        public Builder rawJson(String rawJson) {
            this.rawJson = rawJson;
            return this;
        }

        public Builder validJson(boolean validJson) {
            this.validJson = validJson;
            return this;
        }

        public Builder tokensUsed(int tokensUsed) {
            this.tokensUsed = tokensUsed;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder products(List<Product> products) {
            this.products = products;
            return this;
        }

        public Builder totalResults(Integer totalResults) {
            this.totalResults = totalResults;
            return this;
        }

        public Builder presentFields(Map<String, Boolean> presentFields) {
            this.presentFields = presentFields;
            return this;
        }

        public LlmResponse build() {
            return new LlmResponse(this);
        }
    }
}

