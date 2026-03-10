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
import org.javai.punit.reporting.ConsoleVerdictSink;

/**
 * CLI entrypoint for Sentinel execution.
 *
 * <p>Reads the list of {@code @Sentinel}-annotated classes from a build-time
 * manifest ({@value #MANIFEST_RESOURCE}), builds a {@link SentinelConfiguration},
 * and dispatches to the appropriate {@link SentinelRunner} method.
 *
 * <h2>Usage</h2>
 * <pre>
 * java -jar sentinel.jar --help
 * java -jar sentinel.jar --list
 * java -jar sentinel.jar test [--verbose] [--useCase &lt;id&gt;]
 * java -Dpunit.spec.dir=/path/to/specs -jar sentinel.jar exp [--verbose] [--useCase &lt;id&gt;]
 * </pre>
 *
 * <h2>Exit codes</h2>
 * <ul>
 *   <li>0 — all tests/experiments passed (or --help/--list)</li>
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
        // Parse flags, options, and command
        ParsedArgs parsed = parseArgs();

        if (parsed.help) {
            printHelp(System.out);
            system.exit(EXIT_SUCCESS);
            return;
        }

        if (parsed.list) {
            runList();
            return;
        }

        if (parsed.command == null) {
            printHelp(System.err);
            system.exit(EXIT_USAGE);
            return;
        }

        if (!parsed.command.equals("test") && !parsed.command.equals("exp")
                && !parsed.command.equals("experiment")) {
            System.err.println("Unknown command: " + parsed.command);
            printHelp(System.err);
            system.exit(EXIT_USAGE);
            return;
        }

        boolean isTest = parsed.command.equals("test");

        // For experiments, spec dir is mandatory
        if (!isTest) {
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

        // Discover sentinel classes
        List<Class<?>> sentinelClasses = discoverSentinelClasses();
        if (sentinelClasses == null) {
            return; // error already reported and exit called
        }

        // Build configuration and run
        SentinelConsoleReporter reporter = new SentinelConsoleReporter(System.out);
        SentinelConfiguration.Builder configBuilder = SentinelConfiguration.builder()
                .sentinelClasses(sentinelClasses);
        if (parsed.verbose && isTest) {
            configBuilder.verdictSink(new ConsoleVerdictSink());
        }
        SentinelConfiguration config = configBuilder.build();

        SentinelRunner runner = new SentinelRunner(config);
        if (parsed.verbose) {
            runner.setProgressListener(reporter);
        }
        SentinelResult result;

        if (isTest) {
            result = parsed.useCase != null
                    ? runner.runTests(parsed.useCase)
                    : runner.runTests();
            reporter.printTestSummary(result);
        } else {
            result = parsed.useCase != null
                    ? runner.runExperiments(parsed.useCase)
                    : runner.runExperiments();
            reporter.printExperimentSummary(result);
        }

        system.exit(result.allPassed() ? EXIT_SUCCESS : EXIT_FAILURE);
    }

    private void runList() {
        List<Class<?>> sentinelClasses = discoverSentinelClasses();
        if (sentinelClasses == null) {
            return;
        }

        SentinelConfiguration config = SentinelConfiguration.builder()
                .sentinelClasses(sentinelClasses)
                .build();
        SentinelRunner runner = new SentinelRunner(config);
        SentinelRunner.UseCaseCatalog catalog = runner.listUseCases();

        SentinelConsoleReporter reporter = new SentinelConsoleReporter(System.out);
        reporter.printUseCaseCatalog(catalog);
        system.exit(EXIT_SUCCESS);
    }

    /**
     * Discovers sentinel classes from the manifest. Returns null if discovery
     * fails (error is reported and exit is called).
     */
    private List<Class<?>> discoverSentinelClasses() {
        List<Class<?>> sentinelClasses;
        try {
            sentinelClasses = loadSentinelClasses();
        } catch (SentinelExecutionException e) {
            System.err.println(e.getMessage());
            system.exit(EXIT_USAGE);
            return null;
        }

        if (sentinelClasses.isEmpty()) {
            System.err.println(
                    "No @Sentinel classes found. Ensure the JAR was built with the " +
                    "createSentinel Gradle task, which generates the " + MANIFEST_RESOURCE +
                    " manifest.");
            system.exit(EXIT_USAGE);
            return null;
        }
        return sentinelClasses;
    }

    // ── Argument parsing ─────────────────────────────────────────────────

    static class ParsedArgs {
        String command;
        boolean verbose;
        boolean help;
        boolean list;
        String useCase;
    }

    ParsedArgs parseArgs() {
        ParsedArgs parsed = new ParsedArgs();
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> parsed.help = true;
                case "--list" -> parsed.list = true;
                case "--verbose" -> parsed.verbose = true;
                case "--useCase" -> {
                    if (i + 1 < args.length) {
                        parsed.useCase = args[++i];
                    } else {
                        System.err.println("--useCase requires a value");
                        parsed.help = true;
                    }
                }
                default -> positional.add(args[i]);
            }
        }

        if (!positional.isEmpty()) {
            parsed.command = positional.getFirst();
        }
        return parsed;
    }

    // ── Help ─────────────────────────────────────────────────────────────

    private void printHelp(java.io.PrintStream stream) {
        stream.println("PUnit Sentinel — probabilistic test and experiment runner");
        stream.println();
        stream.println("Usage: java [-Dpunit.spec.dir=<dir>] -jar sentinel.jar <command> [options]");
        stream.println();
        stream.println("Commands:");
        stream.println("  test         Run probabilistic tests against baseline specs");
        stream.println("  exp          Run experiments to produce baseline specs");
        stream.println();
        stream.println("Options:");
        stream.println("  --help       Show this help message and exit");
        stream.println("  --list       List available use cases and exit");
        stream.println("  --verbose    Show per-sample progress during execution");
        stream.println("  --useCase <id>  Run only the specified use case");
        stream.println();
        stream.println("Configuration:");
        stream.println("  -Dpunit.spec.dir=<dir>   Spec directory (JVM system property)");
        stream.println("  PUNIT_SPEC_DIR=<dir>     Spec directory (environment variable)");
        stream.println();
        stream.println("  For experiments (exp), the spec directory is required.");
        stream.println();
        stream.println("Exit codes:");
        stream.println("  0  All tests/experiments passed (or --help/--list)");
        stream.println("  1  One or more tests/experiments failed");
        stream.println("  2  Usage error");
    }

    // ── Infrastructure ───────────────────────────────────────────────────

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
