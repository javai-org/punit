package org.javai.punit.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javai.punit.verdict.ProbabilisticTestVerdict;
import org.javai.punit.verdict.VerdictSink;

/**
 * A {@link VerdictSink} that writes each verdict as an XML file.
 *
 * <p>Each verdict produces one file at:
 * {@code {outputDir}/{className}.{methodName}.xml}
 */
public final class VerdictXmlSink implements VerdictSink {

    private static final Logger logger = LogManager.getLogger(VerdictXmlSink.class);

    private final VerdictXmlWriter writer = new VerdictXmlWriter();
    private final ReportConfiguration config;

    public VerdictXmlSink(ReportConfiguration config) {
        this.config = config;
    }

    @Override
    public void accept(ProbabilisticTestVerdict verdict) {
        if (!config.enabled()) {
            return;
        }

        Path xmlFile = config.xmlFilePath(
                verdict.identity().className(),
                verdict.identity().methodName());

        try {
            Files.createDirectories(xmlFile.getParent());
            try (OutputStream out = Files.newOutputStream(xmlFile)) {
                writer.write(verdict, out);
            }
            logger.debug("Wrote verdict XML: {}", xmlFile);
        } catch (IOException | XMLStreamException e) {
            logger.warn("Failed to write verdict XML to {}: {}", xmlFile, e.getMessage());
        }
    }
}
