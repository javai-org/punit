package org.javai.punit.sentinel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * CLI entrypoint for Sentinel execution.
 *
 * <p>Reads the list of {@code @Sentinel}-annotated classes from a build-time
 * manifest ({@value #MANIFEST_RESOURCE}), builds a {@link SentinelConfiguration},
 * and dispatches to the appropriate {@link SentinelRunner} method.
 *
 * <h2>Usage</h2>
 * <pre>
 * java -Dpunit.spec.dir=/path/to/specs -jar sentinel.jar test
 * java -Dpunit.spec.dir=/path/to/specs -jar sentinel.jar exp
 * </pre>
 *
 * <h2>Exit codes</h2>
 * <ul>
 *   <li>0 — all tests/experiments passed</li>
 *   <li>1 — one or more tests/experiments failed</li>
 *   <li>2 — usage error (bad arguments, missing configuration)</li>
 * </ul>
 */
public class SentinelMain {

    static final String MANIFEST_RESOURCE = "META-INF/punit/sentinel-classes";
    static final int EXIT_SUCCESS = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_USAGE = 2;

    private static final String PROP_SPEC_DIR = "punit.spec.dir";
    private static final String ENV_SPEC_DIR = "PUNIT_SPEC_DIR";

    private final String[] args;
    private final SystemBridge system;

    /**
     * Abstraction over system-level operations to enable testing without
     * {@code System.exit()} or real environment lookups.
     */
    interface SystemBridge {
        void exit(int code);
        String getProperty(String key);
        String getenv(String name);
        ClassLoader getClassLoader();
    }

    SentinelMain(String[] args, SystemBridge system) {
        this.args = args;
        this.system = system;
    }

    public static void main(String[] args) {
        new SentinelMain(args, new RealSystemBridge()).run();
    }

    void run() {
        if (args.length < 1) {
            printUsage();
            system.exit(EXIT_USAGE);
            return;
        }

        String command = args[0];
        if (!command.equals("test") && !command.equals("exp") && !command.equals("experiment")) {
            System.err.println("Unknown command: " + command);
            printUsage();
            system.exit(EXIT_USAGE);
            return;
        }

        // For experiments, spec dir is mandatory (experiments write specs)
        if (command.equals("exp") || command.equals("experiment")) {
            String specDir = resolveSpecDir();
            if (specDir == null) {
                System.err.println(
                        "Experiment mode requires a spec output directory. " +
                        "Set -D" + PROP_SPEC_DIR + "=<dir> or " + ENV_SPEC_DIR + " environment variable.");
                system.exit(EXIT_USAGE);
                return;
            }
            Path specPath = Paths.get(specDir);
            if (Files.exists(specPath) && !Files.isWritable(specPath)) {
                System.err.println("Spec directory is not writable: " + specPath);
                system.exit(EXIT_USAGE);
                return;
            }
        }

        // Discover sentinel classes from manifest
        List<Class<?>> sentinelClasses;
        try {
            sentinelClasses = loadSentinelClasses();
        } catch (SentinelExecutionException e) {
            System.err.println(e.getMessage());
            system.exit(EXIT_USAGE);
            return;
        }

        if (sentinelClasses.isEmpty()) {
            System.err.println(
                    "No @Sentinel classes found. Ensure the JAR was built with the " +
                    "createSentinel Gradle task, which generates the " + MANIFEST_RESOURCE +
                    " manifest.");
            system.exit(EXIT_USAGE);
            return;
        }

        // Build configuration and run
        SentinelConfiguration config = SentinelConfiguration.builder()
                .sentinelClasses(sentinelClasses)
                .build();

        SentinelRunner runner = new SentinelRunner(config);
        SentinelResult result;

        if (command.equals("test")) {
            result = runner.runTests();
        } else {
            result = runner.runExperiments();
        }

        // Print summary
        printSummary(command, result);

        system.exit(result.allPassed() ? EXIT_SUCCESS : EXIT_FAILURE);
    }

    private String resolveSpecDir() {
        String dir = system.getProperty(PROP_SPEC_DIR);
        if (dir != null && !dir.isEmpty()) {
            return dir;
        }
        dir = system.getenv(ENV_SPEC_DIR);
        if (dir != null && !dir.isEmpty()) {
            return dir;
        }
        return null;
    }

    List<Class<?>> loadSentinelClasses() {
        List<String> classNames = loadManifestEntries();
        List<Class<?>> classes = new ArrayList<>();
        ClassLoader classLoader = system.getClassLoader();

        for (String className : classNames) {
            try {
                classes.add(Class.forName(className, true, classLoader));
            } catch (ClassNotFoundException e) {
                throw new SentinelExecutionException(
                        "Sentinel class not found on classpath: " + className, e);
            }
        }
        return classes;
    }

    private List<String> loadManifestEntries() {
        List<String> entries = new ArrayList<>();
        ClassLoader classLoader = system.getClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(MANIFEST_RESOURCE);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            entries.add(trimmed);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new SentinelExecutionException(
                    "Failed to read sentinel class manifest: " + MANIFEST_RESOURCE, e);
        }

        return entries;
    }

    private void printSummary(String command, SentinelResult result) {
        String mode = command.equals("test") ? "Test" : "Experiment";
        System.out.println();
        System.out.println("Sentinel " + mode + " Summary");
        System.out.println("─".repeat(40));
        System.out.println("Total:   " + result.totalTests());
        System.out.println("Passed:  " + result.passed());
        System.out.println("Failed:  " + result.failed());
        System.out.println("Skipped: " + result.skipped());
        System.out.println("Duration: " + result.totalDuration().toMillis() + "ms");
        System.out.println();

        if (result.allPassed()) {
            System.out.println("Result: PASS");
        } else {
            System.out.println("Result: FAIL");
            result.verdicts().stream()
                    .filter(v -> !v.passed())
                    .forEach(v -> System.out.println("  FAILED: " + v.testName()));
        }
    }

    private void printUsage() {
        System.err.println("Usage: java [-Dpunit.spec.dir=<dir>] -jar sentinel.jar <command>");
        System.err.println();
        System.err.println("Commands:");
        System.err.println("  test  Run probabilistic tests against baseline specs");
        System.err.println("  exp   Run experiments to produce baseline specs");
        System.err.println();
        System.err.println("The spec directory can be set via:");
        System.err.println("  -Dpunit.spec.dir=<dir>   JVM system property");
        System.err.println("  PUNIT_SPEC_DIR=<dir>     Environment variable");
        System.err.println();
        System.err.println("For experiments (exp), the spec directory is required.");
    }

    private static class RealSystemBridge implements SystemBridge {
        @Override
        public void exit(int code) {
            System.exit(code);
        }

        @Override
        public String getProperty(String key) {
            return System.getProperty(key);
        }

        @Override
        public String getenv(String name) {
            return System.getenv(name);
        }

        @Override
        public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }
    }
}
