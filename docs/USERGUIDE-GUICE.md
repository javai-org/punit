# PUnit with Google Guice

This guide explains how to use PUnit in applications that use Google Guice for dependency injection.

> **Note:** PUnit has no dependency on Guice. This guide shows how to integrate PUnit with your Guice injector.

---

## Overview

In Guice applications, use cases are typically managed by the injector with their dependencies wired automatically. PUnit needs access to these instances to:
- Invoke use case functionality during experiments and tests
- Call `@CovariateSource` methods to resolve covariate values

---

## Setting Up the UseCaseProvider

### 1. Create a Guice-Aware Provider

```java
import com.google.inject.Injector;
import org.javai.punit.api.UseCaseProvider;

public class GuiceUseCaseProvider implements UseCaseProvider {
    
    private final Injector injector;
    
    public GuiceUseCaseProvider(Injector injector) {
        this.injector = injector;
    }
    
    @Override
    public <T> T getInstance(Class<T> useCaseClass) {
        return injector.getInstance(useCaseClass);
    }
    
    @Override
    public boolean supports(Class<?> useCaseClass) {
        try {
            injector.getBinding(useCaseClass);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 2. Register with PUnit

In your test setup:

```java
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeAll;
import org.javai.punit.api.PUnit;
import org.javai.punit.api.UseCaseRegistry;

public abstract class BasePUnitTest {
    
    protected static Injector injector;
    
    @BeforeAll
    static void setupPUnit() {
        injector = Guice.createInjector(new AppModule(), new TestModule());
        
        PUnit.setUseCaseRegistry(
            UseCaseRegistry.withDefaults()
                .register(new GuiceUseCaseProvider(injector))
        );
    }
}
```

---

## Example: Use Case with Guice

### The Use Case

```java
import javax.inject.Inject;
import javax.inject.Named;
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
public class ProductSearchUseCase {
    
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
    
    public SearchResult search(String query) {
        return llmClient.search(query);
    }
}
```

### The Guice Module

```java
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class AppModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(String.class)
            .annotatedWith(Names.named("llm.model"))
            .toInstance(System.getProperty("llm.model", "gpt-4.1-mini"));
        
        bind(LlmClient.class).to(OpenAiClient.class);
    }
}
```

### The Experiment

```java
import org.javai.punit.api.Experiment;
import org.javai.punit.api.ResultCaptor;

public class ProductSearchExperiment extends BasePUnitTest {
    
    @Experiment(useCase = ProductSearchUseCase.class)
    void measureSearch(ProductSearchUseCase useCase, ResultCaptor captor) {
        // 'useCase' is obtained from Guice, fully initialized
        captor.capture(useCase.search("laptop"));
    }
}
```

### The Probabilistic Test

```java
import org.javai.punit.api.ProbabilisticTest;

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

Use Guice modules to vary configuration:

```java
public class ProductionModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(String.class)
            .annotatedWith(Names.named("llm.model"))
            .toInstance("gpt-4.1-mini");
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
static void setup() {
    injector = Guice.createInjector(
        Modules.override(new AppModule()).with(new TestModule())
    );
    PUnit.setUseCaseRegistry(
        UseCaseRegistry.withDefaults()
            .register(new GuiceUseCaseProvider(injector))
    );
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

### "No binding for use case class"

Ensure your use case class is bound in a Guice module, or has an `@Inject` constructor that Guice can satisfy.

### Covariate values not resolving

Verify that `@CovariateSource` methods are public and return `String` or `CovariateValue`.


