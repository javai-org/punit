# PUnit with Spring Boot

This guide explains how to use PUnit in Spring Boot applications.

> **Note:** PUnit has no dependency on Spring. This guide shows how to integrate PUnit with your Spring context.

---

## Overview

In Spring applications, use cases are typically Spring beans with injected dependencies. PUnit needs access to these beans to:
- Invoke use case functionality during experiments and tests
- Call `@CovariateSource` methods to resolve covariate values

---

## Setting Up the UseCaseProvider

### 1. Create a Spring-Aware Provider

```java
import org.springframework.context.ApplicationContext;
import org.javai.punit.api.UseCaseProvider;

public class SpringUseCaseProvider implements UseCaseProvider {
    
    private final ApplicationContext context;
    
    public SpringUseCaseProvider(ApplicationContext context) {
        this.context = context;
    }
    
    @Override
    public <T> T getInstance(Class<T> useCaseClass) {
        return context.getBean(useCaseClass);
    }
    
    @Override
    public boolean supports(Class<?> useCaseClass) {
        return context.getBeanNamesForType(useCaseClass).length > 0;
    }
}
```

### 2. Register with PUnit

In your test configuration or base test class:

```java
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.javai.punit.api.PUnit;
import org.javai.punit.api.UseCaseRegistry;

@SpringBootTest
public abstract class BasePUnitTest {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @BeforeAll
    void setupPUnit() {
        PUnit.setUseCaseRegistry(
            UseCaseRegistry.withDefaults()
                .register(new SpringUseCaseProvider(applicationContext))
        );
    }
}
```

---

## Example: Use Case as a Spring Bean

### The Use Case

```java
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.javai.punit.api.UseCase;
import org.javai.punit.api.CovariateSource;
import org.javai.punit.api.StandardCovariate;
import org.javai.punit.api.CovariateCategory;

@UseCase(
    value = "ProductSearch",
    covariates = { StandardCovariate.TIME_OF_DAY },
    customCovariates = {
        @Covariate(key = "llm_model", category = CovariateCategory.CONFIGURATION)
    }
)
@Component
public class ProductSearchUseCase {
    
    private final LlmClient llmClient;
    
    @Value("${app.llm.model}")
    private String modelName;
    
    public ProductSearchUseCase(LlmClient llmClient) {
        this.llmClient = llmClient;
    }
    
    @CovariateSource("llm_model")
    public String getLlmModel() {
        return modelName;  // Reads from Spring configuration
    }
    
    public SearchResult search(String query) {
        return llmClient.search(query);
    }
}
```

### The Experiment

```java
import org.javai.punit.api.Experiment;
import org.javai.punit.api.ResultCaptor;

@SpringBootTest
public class ProductSearchExperiment extends BasePUnitTest {
    
    @Experiment(useCase = ProductSearchUseCase.class)
    void measureSearch(ProductSearchUseCase useCase, ResultCaptor captor) {
        // 'useCase' is the Spring bean, fully initialized
        captor.capture(useCase.search("laptop"));
    }
}
```

### The Probabilistic Test

```java
import org.javai.punit.api.ProbabilisticTest;

@SpringBootTest
public class ProductSearchTest extends BasePUnitTest {
    
    @ProbabilisticTest(useCase = ProductSearchUseCase.class)
    void testSearchQuality(ProductSearchUseCase useCase, StatisticalAssertions stats) {
        SearchResult result = useCase.search("laptop");
        stats.assertPassRate(result::isRelevant, 0.95);
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
    model: gpt-4.1-mini

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

### "No bean found for use case class"

Ensure your use case class is annotated with `@Component` (or `@Service`, `@Repository`, etc.) and is within a component-scanned package.

### Covariate values not resolving

Verify that `@CovariateSource` methods are public and return `String` or `CovariateValue`.


