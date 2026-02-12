# PUnit with Spring Boot

This guide explains how to use PUnit in Spring Boot applications.

> **Note:** PUnit has no dependency on Spring. This guide shows how to register Spring-managed use cases with PUnit's `UseCaseProvider`.

---

## Overview

In Spring applications, use cases are typically Spring beans with injected dependencies. PUnit needs access to these beans to:
- Invoke use case functionality during experiments and tests
- Call `@CovariateSource` methods to resolve covariate values

The integration is straightforward: register a factory with PUnit's `UseCaseProvider` that delegates instance creation to Spring's `ApplicationContext`.

---

## Registering Spring-Managed Use Cases

### Basic Setup

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.javai.punit.api.UseCaseProvider;

@SpringBootTest
public abstract class BasePUnitTest {

    @Autowired
    protected ApplicationContext applicationContext;

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void registerUseCases() {
        provider.register(ProductSearchUseCase.class,
                () -> applicationContext.getBean(ProductSearchUseCase.class));
    }
}
```

The `UseCaseProvider` is registered via JUnit 5's `@RegisterExtension`. In `@BeforeEach`, you register a factory that delegates to Spring's `ApplicationContext`. PUnit calls this factory whenever it needs an instance of the use case.

**Alternative: Injecting specific dependencies**

If you prefer to construct the use case manually with specific injected dependencies:

```java
@SpringBootTest
public abstract class BasePUnitTest {

    @Autowired
    private LlmClient llmClient;

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeEach
    void registerUseCases() {
        provider.register(ProductSearchUseCase.class,
                () -> new ProductSearchUseCase(llmClient, "gpt-4o-mini"));
    }
}
```

This approach gives you explicit control over how the use case is constructed.

---

## Example: Use Case as a Spring Bean

### The Use Case

```java
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.CovariateSource;
import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.Covariate;
import org.javai.punit.contract.ServiceContract;
import org.javai.punit.contract.UseCaseOutcome;
import org.javai.outcome.Outcome;

@UseCase(
    value = "ProductSearch",
    covariateTimeOfDay = {"08:00/4h", "16:00/4h"},
    covariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION)
    }
)
@Component
public class ProductSearchUseCase {

    private static final ServiceContract<String, SearchResult> CONTRACT =
            ServiceContract.<String, SearchResult>define()
                    .ensure("Result is relevant", result ->
                            result.isRelevant()
                                    ? Outcome.ok()
                                    : Outcome.fail("relevance", "Result not relevant"))
                    .build();

    private final LlmClient llmClient;

    @Value("${app.llm.model}")
    private String modelName;

    public ProductSearchUseCase(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @CovariateSource("llm_model")
    public String getLlmModel() {
        return modelName;
    }

    public UseCaseOutcome<SearchResult> search(String query) {
        return UseCaseOutcome
                .withContract(CONTRACT)
                .input(query)
                .execute(q -> llmClient.search(q))
                .build();
    }
}
```

The `ServiceContract` defines what "success" means. The `search` method returns a `UseCaseOutcome` that evaluates the contract's postconditions. This same outcome is used by both experiments and tests, ensuring they measure and verify the same criteria. See [Part 1 of the User Guide](USER-GUIDE.md#part-1-the-shopping-basket-domain) for a full discussion of service contracts.

### The Experiment

```java
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;

@SpringBootTest
public class ProductSearchExperiment extends BasePUnitTest {

    @MeasureExperiment(useCase = ProductSearchUseCase.class, samples = 100)
    void measureSearch(ProductSearchUseCase useCase, OutcomeCaptor captor) {
        captor.record(useCase.search("laptop"));
    }
}
```

### The Probabilistic Test

```java
import org.javai.punit.api.ProbabilisticTest;

@SpringBootTest
public class ProductSearchTest extends BasePUnitTest {

    @ProbabilisticTest(
        useCase = ProductSearchUseCase.class,
        samples = 100,
        minPassRate = 0.95
    )
    void testSearchQuality(ProductSearchUseCase useCase) {
        useCase.search("laptop").assertAll();
    }
}
```

---

## Configuration by Environment

Spring's profile mechanism works naturally with PUnit covariates:

```yaml
# application.yml
app:
  llm:
    model: gpt-4o-mini

# application-staging.yml
app:
  llm:
    model: gpt-3.5-turbo
```

The `@CovariateSource("llm_model")` method returns whatever the active profile specifies. Different profiles create different baselines automatically.

---

## Tips

### Use `@DirtiesContext` Sparingly

If your use case modifies application state, consider whether this affects baseline comparability.

### Test Slices

For faster experiments, consider using test slices (`@WebMvcTest`, `@DataJpaTest`) if your use case doesn't need the full context.

### Mocking External Services

When running experiments, you may want real external service behavior. When running probabilistic tests, you may want mocked responses for consistency. Use Spring profiles or `@MockBean` accordingly.

---

## Troubleshooting

### "No factory registered for use case"

Ensure you have registered a factory for your use case class in `@BeforeEach`:

```java
provider.register(ProductSearchUseCase.class,
        () -> applicationContext.getBean(ProductSearchUseCase.class));
```

Verify that your use case class is annotated with `@Component` (or `@Service`, `@Repository`, etc.) and is within a component-scanned package.

### Covariate values not resolving

Verify that `@CovariateSource` methods are public, take no parameters, and return `String` or `CovariateValue`.
