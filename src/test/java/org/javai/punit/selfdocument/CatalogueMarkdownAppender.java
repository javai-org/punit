package org.javai.punit.selfdocument;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.javai.punit.reporting.PUnitReporter;

/**
 * Custom Log4j2 appender that captures PUnit verdict output as markdown.
 *
 * <p>Each verdict is written as a markdown section with the title extracted from
 * the {@code ═ TITLE ═══...} header divider, and the full output wrapped in a
 * fenced code block.
 *
 * <p>This appender is self-contained — it programmatically registers itself with
 * the PUnitReporter logger and requires no external Log4j2 configuration changes.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CatalogueMarkdownAppender appender = CatalogueMarkdownAppender.install();
 * // ... run tests ...
 * appender.remove();
 * }</pre>
 *
 * @see VerdictCatalogueTest
 */
final class CatalogueMarkdownAppender extends AbstractAppender {

    private static final String LOGGER_NAME = PUnitReporter.class.getName();
    private static final Pattern TITLE_PATTERN = Pattern.compile("^═ (.+?) ═+.*$", Pattern.MULTILINE);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private final PrintWriter writer;
    private final Path outputPath;

    private CatalogueMarkdownAppender(String name, PrintWriter writer, Path outputPath) {
        super(name, null, PatternLayout.createDefaultLayout(), false, null);
        this.writer = writer;
        this.outputPath = outputPath;
    }

    /**
     * Installs the appender on the PUnitReporter logger and writes the markdown header.
     *
     * <p>The output file name is derived from the current detail level system property:
     * {@code build/verdict-catalogue-{LEVEL}.md}.
     *
     * @return the installed appender (call {@link #remove()} in {@code @AfterAll})
     */
    static CatalogueMarkdownAppender install() {
        String detailLevel = System.getProperty("punit.stats.detailLevel", "VERBOSE");
        Path outputPath = Path.of("build", "verdict-catalogue-" + detailLevel + ".md");

        try {
            Files.createDirectories(outputPath.getParent());
            PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath));

            writer.println("# PUnit Verdict Catalogue");
            writer.println();
            writer.printf("Generated: %s%n", TIMESTAMP_FORMAT.format(Instant.now()));
            writer.printf("Detail level: %s%n", detailLevel);
            writer.println();
            writer.println("---");
            writer.println();
            writer.flush();

            CatalogueMarkdownAppender appender =
                    new CatalogueMarkdownAppender("CatalogueMarkdown", writer, outputPath);
            appender.start();

            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);
            if (!loggerConfig.getName().equals(LOGGER_NAME)) {
                loggerConfig = new LoggerConfig(LOGGER_NAME, Level.INFO, false);
                config.addLogger(LOGGER_NAME, loggerConfig);
            }
            loggerConfig.addAppender(appender, Level.INFO, null);
            context.updateLoggers();

            return appender;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create catalogue file: " + outputPath, e);
        }
    }

    @Override
    public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();

        String title = extractTitle(message);
        writer.println("## " + title);
        writer.println();
        writer.println("```");
        writer.println(message);
        writer.println("```");
        writer.println();
        writer.println("---");
        writer.println();
        writer.flush();
    }

    /**
     * Removes this appender from the PUnitReporter logger and closes the output file.
     */
    void remove() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LOGGER_NAME);
        loggerConfig.removeAppender(getName());
        context.updateLoggers();

        stop();
        writer.close();
    }

    /**
     * Returns the path to the generated markdown file.
     */
    Path getOutputPath() {
        return outputPath;
    }

    private static String extractTitle(String message) {
        Matcher matcher = TITLE_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Untitled Verdict";
    }
}
