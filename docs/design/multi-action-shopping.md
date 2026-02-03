# Design: Multi-Action Shopping Use Case Enhancement

## Summary

Enhance the shopping basket use case to consistently demonstrate multi-action LLM responses, making it a better teaching example for PUnit's instance conformance capabilities.

## Background

The current shopping use case can handle both single actions and arrays of actions:

```java
// ShoppingActionValidator already supports both formats
if (root.isArray()) {
    return parseActionArray(root);
} else if (root.isObject()) {
    return parseSingleAction(root);
}
```

However, the current approach has inconsistencies:
1. The system prompt instructs the LLM to return single objects
2. Most golden dataset entries expect single actions
3. The USER-GUIDE.md shows a fictional JSON format that doesn't match the actual code

## Goals

1. **Consistency**: Always expect arrays of actions, even for single-action instructions
2. **Clarity**: Make the JSON schema intuitive and well-documented
3. **Teaching Focus**: Use the enhancement to demonstrate PUnit's instance conformance testing
4. **Minimal Disruption**: Keep changes focused on the example code, not framework internals

## Non-Goals

- Changing the core PUnit framework
- Adding new framework features
- Making the shopping domain model production-ready (it's a teaching example)

## Design

### 1. JSON Schema Evolution

**Current format (single action):**
```json
{
  "context": "SHOP",
  "name": "add",
  "parameters": [
    {"name": "item", "value": "apples"},
    {"name": "quantity", "value": "2"}
  ]
}
```

**Proposed format (always array):**
```json
{
  "actions": [
    {
      "context": "SHOP",
      "name": "add",
      "parameters": [
        {"name": "item", "value": "apples"},
        {"name": "quantity", "value": "2"}
      ]
    }
  ]
}
```

**Rationale:**
- Consistent structure regardless of action count
- Self-documenting (top-level `actions` key clarifies the response type)
- Easier to validate and compare in instance conformance tests
- Better represents real-world scenarios where LLMs batch operations

### 2. Files to Modify

| File | Changes |
|------|---------|
| `ShoppingAction.java` | Add `ShoppingResponse` wrapper record with `actions` list |
| `ShoppingActionValidator.java` | Update to expect `actions` wrapper, with backward compatibility for bare arrays |
| `ShoppingBasketUseCase.java` | Update system prompt to request the new format |
| `shopping-instructions.json` | Update all expected values to use wrapper format |
| `USER-GUIDE.md` | Update example output to match actual format |

### 3. Implementation Details

#### 3.1 ShoppingResponse Wrapper

Add a new record to wrap the actions list:

```java
/**
 * Response envelope containing one or more shopping actions.
 */
public record ShoppingResponse(
    @JsonProperty("actions") List<ShoppingAction> actions
) {
    public ShoppingResponse {
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Response must contain at least one action");
        }
        actions = List.copyOf(actions);
    }

    @JsonCreator
    public static ShoppingResponse fromJson(
            @JsonProperty("actions") List<ShoppingAction> actions) {
        return new ShoppingResponse(actions);
    }
}
```

#### 3.2 Validator Updates

Update `ShoppingActionValidator` to expect the wrapped `{"actions": [...]}` format:

```java
static Outcome<ValidationResult> validate(ChatResponse response) {
    // ... null checks ...

    JsonNode root = MAPPER.readTree(json);

    if (!root.isObject() || !root.has("actions")) {
        return Outcome.fail("validation", "Expected JSON object with 'actions' array");
    }

    JsonNode actionsNode = root.get("actions");
    if (!actionsNode.isArray()) {
        return Outcome.fail("validation", "Expected 'actions' to be an array");
    }

    return parseActionArray(actionsNode);
}
```

#### 3.3 System Prompt Update

Update the system prompt in `ShoppingBasketUseCase.java`:

```java
private String systemPrompt = """
    You are a shopping assistant that converts natural language instructions into JSON actions.

    ALWAYS respond with a JSON object containing an "actions" array, even for single operations.

    Format:
    {
      "actions": [
        {
          "context": "SHOP",
          "name": "<action>",
          "parameters": [
            {"name": "<param_name>", "value": "<param_value>"}
          ]
        }
      ]
    }

    Valid actions for SHOP context: "add", "remove", "clear"

    Common parameters:
    - "item": the product name
    - "quantity": the number of items (as a string)

    For "clear" actions, parameters may be empty.

    Examples:
    - "Add 2 apples" → {"actions": [{"context": "SHOP", "name": "add", "parameters": [{"name": "item", "value": "apples"}, {"name": "quantity", "value": "2"}]}]}
    - "Add apples and remove milk" → {"actions": [{"context": "SHOP", "name": "add", ...}, {"context": "SHOP", "name": "remove", ...}]}
    """;
```

#### 3.4 Golden Dataset Update

Update `shopping-instructions.json` to use the wrapped format:

```json
[
  {
    "instruction": "Add 2 apples",
    "expected": "{\"actions\":[{\"context\":\"SHOP\",\"name\":\"add\",\"parameters\":[{\"name\":\"item\",\"value\":\"apples\"},{\"name\":\"quantity\",\"value\":\"2\"}]}]}"
  },
  {
    "instruction": "Add 3 oranges and 2 bananas",
    "expected": "{\"actions\":[{\"context\":\"SHOP\",\"name\":\"add\",\"parameters\":[{\"name\":\"item\",\"value\":\"oranges\"},{\"name\":\"quantity\",\"value\":\"3\"}]},{\"context\":\"SHOP\",\"name\":\"add\",\"parameters\":[{\"name\":\"item\",\"value\":\"bananas\"},{\"name\":\"quantity\",\"value\":\"2\"}]}]}"
  }
]
```

### 4. Impact on PUnit Examples

The enhancement improves the teaching value of several examples:

| Example | Improvement |
|---------|-------------|
| `ShoppingBasketTest` | Shows instance conformance with array matching |
| `ShoppingBasketMeasure` | Measures success rate with consistent format |
| `ShoppingBasketOptimizePrompt` | Optimizes prompts for multi-action responses |

### 5. Documentation Updates

Update USER-GUIDE.md section "The Service Contract" to show:

```markdown
When we test with instance conformance, the expected output looks like:

```json
{
  "actions": [
    {
      "context": "SHOP",
      "name": "add",
      "parameters": [
        {"name": "item", "value": "apples"},
        {"name": "quantity", "value": "2"}
      ]
    }
  ]
}
```

This format supports both simple single-action instructions and complex
multi-operation requests like "Add 3 oranges and remove the milk".
```

## Testing Strategy

1. **Unit tests**: Update `ShoppingActionValidatorTest` (if exists) or add new tests
2. **Backward compatibility**: Verify validator still handles bare arrays/objects
3. **Integration**: Run existing shopping experiments to verify they still work
4. **Golden dataset**: Run measure experiment to verify updated expectations

## Rollout

1. Create feature branch (done: `feature/multi-action-shopping`)
2. Add `ShoppingResponse` wrapper record
3. Update validator with backward compatibility
4. Update system prompt
5. Update golden dataset
6. Update USER-GUIDE.md
7. Run all tests
8. Create PR for review

## Open Questions

1. **Parameter names**: Should we simplify parameters from `[{"name": "item", "value": "..."}]` to `{"item": "...", "quantity": "..."}`?
   - **Decision**: Keep current format - it's more general and demonstrates the flexibility of the action DSL

2. **Context field**: Is the `context` field still needed if we're only using SHOP?
   - **Decision**: Keep it - demonstrates how the pattern extends to other domains (RECIPE context exists)

## Appendix: JSON Format Examples

| Instruction | Expected JSON |
|-------------|---------------|
| "Add 2 apples" | `{"actions":[{"context":"SHOP","name":"add","parameters":[{"name":"item","value":"apples"},{"name":"quantity","value":"2"}]}]}` |
| "Clear basket" | `{"actions":[{"context":"SHOP","name":"clear","parameters":[]}]}` |
| "Add oranges and bananas" | `{"actions":[{"context":"SHOP","name":"add",...},{"context":"SHOP","name":"add",...}]}` |
