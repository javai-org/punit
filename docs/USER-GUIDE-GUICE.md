# PUnit with Google Guice

This guide explains how to use PUnit in applications that use Google Guice for dependency injection.

> **Note:** PUnit has no dependency on Guice. This guide shows how to register Guice-managed use cases with PUnit's `UseCaseProvider`.

---

## Overview

In Guice applications, use cases are typically managed by the injector with their dependencies wired automatically. PUnit needs access to these instances to:
- Invoke use case functionality during experiments and tests
- Call `@CovariateSource` methods to resolve covariate values

The integration is straightforward: register a factory with PUnit's `UseCaseProvider` that delegates instance creation to your Guice injector.

---

## Registering Guice-Managed Use Cases

### Basic Setup

```java
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.javai.punit.api.UseCaseProvider;

public abstract class BasePUnitTest {

    protected static Injector injector;

    @RegisterExtension
    UseCaseProvider provider = new UseCaseProvider();

    @BeforeAll
    static void createInjector() {
        injector = Guice.createInjector(new AppModule(), new TestModule());
    }

    @BeforeEach
    void registerUseCases() {
        provider.register(ProductSearchUseCase.class,
                () -> injector.getInstance(ProductSearchUseCase.class));
    }
}
```

The `UseCaseProvider` is registered via JUnit 5's `@RegisterExtension`. In `@BeforeEach`, you register a factory that delegates to your Guice injector. PUnit calls this factory whenever it needs an instance of the use case.

---

## Example: Use Case with Guice

### The Use Case

```java
import javax.inject.Inject;
import javax.inject.Named;
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
public class ProductSearchUseCase {

    private static final ServiceContract<String, SearchResult> CONTRACT =
            ServiceContract.<String, SearchResult>define()
                    .ensure("Result is relevant", result ->
                            result.isRelevant()
                                    ? Outcome.ok()
                                    : Outcome.fail("relevance", "Result not relevant"))
                    .build();

    private final LlmClient llmClient;
    private final String modelName;

    @Inject
    public ProductSearchUseCase(
            LlmClient llmClient,
            @Named("llm.model") String modelName) {
        this.llmClient = llmClient;
        this.modelName = modelName;
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

### The Guice Module

```java
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(String.class)
            .annotatedWith(Names.named("llm.model"))
            .toInstance(System.getProperty("llm.model", "gpt-4o-mini"));

        bind(LlmClient.class).to(OpenAiClient.class);
    }
}
```

### The Experiment

```java
import org.javai.punit.api.MeasureExperiment;
import org.javai.punit.api.OutcomeCaptor;

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

Use Guice modules to vary configuration:

```java
public class ProductionModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(String.class)
            .annotatedWith(Names.named("llm.model"))
            .toInstance("gpt-4o-mini");
    }
}

public class StagingModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(String.class)
            .annotatedWith(Names.named("llm.model"))
            .toInstance("gpt-3.5-turbo");
    }
}
```

Select the module based on environment:

```java
Module configModule = isProduction() ? new ProductionModule() : new StagingModule();
injector = Guice.createInjector(new AppModule(), configModule);
```

---

## Using Test Modules

For experiments with mocked dependencies:

```java
public class TestModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(LlmClient.class).to(MockLlmClient.class);
    }
}
```

```java
@BeforeAll
static void createInjector() {
    injector = Guice.createInjector(
        Modules.override(new AppModule()).with(new TestModule())
    );
}

@BeforeEach
void registerUseCases() {
    provider.register(ProductSearchUseCase.class,
            () -> injector.getInstance(ProductSearchUseCase.class));
}
```

---

## Tips

### Scopes

Consider use case scope carefully:
- `@Singleton`: Same instance across all samples (may accumulate state)
- Unscoped: New instance per request (cleaner, but more overhead)

For experiments, unscoped is often safer to avoid state leakage between samples.

### Child Injectors

For test isolation, consider creating a child injector per test class:

```java
Injector testInjector = parentInjector.createChildInjector(new TestOverrides());
```

---

## Troubleshooting

### "No factory registered for use case"

Ensure you have registered a factory for your use case class in `@BeforeEach`:

```java
provider.register(ProductSearchUseCase.class,
        () -> injector.getInstance(ProductSearchUseCase.class));
```

Verify that the use case class is bound in a Guice module, or has an `@Inject` constructor that Guice can satisfy.

### Covariate values not resolving

Verify that `@CovariateSource` methods are public, take no parameters, and return `String` or `CovariateValue`.