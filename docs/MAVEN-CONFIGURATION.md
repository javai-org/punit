# Maven Configuration for PUnit

PUnit's Gradle plugin automates experiment task setup. For Maven users, the equivalent configuration uses Surefire and Failsafe with JUnit 5 tag filtering.

## Test Task Configuration (Surefire)

Exclude experiment-tagged tests from regular test runs:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <excludedGroups>punit-experiment</excludedGroups>
        <systemPropertyVariables>
            <!-- Forward punit.* properties as needed -->
            <punit.stats.detailLevel>${punit.stats.detailLevel}</punit.stats.detailLevel>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

## Experiment Profile

Run experiments via a dedicated Maven profile:

```xml
<profile>
    <id>experiments</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.4</version>
                <configuration>
                    <!-- Include only experiment-tagged tests -->
                    <groups>punit-experiment</groups>

                    <!-- Output directories for each experiment mode -->
                    <systemPropertyVariables>
                        <punit.specs.outputDir>src/test/resources/punit/specs</punit.specs.outputDir>
                        <punit.explorations.outputDir>src/test/resources/punit/explorations</punit.explorations.outputDir>
                        <punit.optimizations.outputDir>src/test/resources/punit/optimizations</punit.optimizations.outputDir>
                    </systemPropertyVariables>

                    <!-- Deactivate @Disabled so experiments can run -->
                    <configurationParameters>
                        junit.jupiter.conditions.deactivate = org.junit.*DisabledCondition
                    </configurationParameters>

                    <!-- Experiments are exploratory — don't fail the build -->
                    <testFailureIgnore>true</testFailureIgnore>

                    <!-- Verbose output -->
                    <reportFormat>plain</reportFormat>
                    <trimStackTrace>false</trimStackTrace>
                    <useFile>false</useFile>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Usage:

```bash
# Run all experiments
mvn test -Pexperiments

# Run a specific experiment class
mvn test -Pexperiments -Dtest=ShoppingBasketMeasure

# Run a specific experiment method
mvn test -Pexperiments -Dtest=ShoppingBasketMeasure#measureRealisticSearchBaseline
```

## Failsafe Alternative

If you prefer to use Failsafe for experiments (keeping Surefire for unit tests):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <groups>punit-experiment</groups>
        <systemPropertyVariables>
            <punit.specs.outputDir>src/test/resources/punit/specs</punit.specs.outputDir>
            <punit.explorations.outputDir>src/test/resources/punit/explorations</punit.explorations.outputDir>
            <punit.optimizations.outputDir>src/test/resources/punit/optimizations</punit.optimizations.outputDir>
        </systemPropertyVariables>
        <configurationParameters>
            junit.jupiter.conditions.deactivate = org.junit.*DisabledCondition
        </configurationParameters>
        <testFailureIgnore>true</testFailureIgnore>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Usage:

```bash
mvn verify -Dgroups=punit-experiment
```

## Test Subject Exclusion

If your project uses PUnit's TestKit pattern (test subjects in `**/testsubjects/**`), exclude them from direct discovery:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/testsubjects/**</exclude>
        </excludes>
    </configuration>
</plugin>
```
