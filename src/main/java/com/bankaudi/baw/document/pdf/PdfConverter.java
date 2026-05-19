package com.bankaudi.baw.document.pdf;

import com.bankaudi.baw.document.api.DocumentGenerationException;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

public class PdfConverter {
    private static final Logger LOG = LoggerFactory.getLogger(PdfConverter.class);

    public byte[] convert(WordprocessingMLPackage wordPackage) throws DocumentGenerationException {
        return convert(0, wordPackage);
    }

    public byte[] convert(long requestId, WordprocessingMLPackage wordPackage) throws DocumentGenerationException {
        long startedAt = System.currentTimeMillis();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(128 * 1024)) {
            long settingsStartedAt = System.currentTimeMillis();
            FOSettings settings = Docx4J.createFOSettings();
            settings.setOpcPackage(wordPackage);
            settings.setApacheFopMime("application/pdf");
            LOG.info("BAW-DOC-TIMING request={} stage=create-fo-settings durationMs={}",
                    requestId, System.currentTimeMillis() - settingsStartedAt);

            long toFoStartedAt = System.currentTimeMillis();
            Docx4J.toFO(settings, outputStream, Docx4J.FLAG_EXPORT_PREFER_XSL);
            LOG.info("BAW-DOC-TIMING request={} stage=docx4j-to-fo durationMs={} outputBytes={}",
                    requestId, System.currentTimeMillis() - toFoStartedAt, outputStream.size());

            long materializeStartedAt = System.currentTimeMillis();
            byte[] pdf = outputStream.toByteArray();
            LOG.info("BAW-DOC-TIMING request={} stage=materialize-pdf-bytes durationMs={} pdfBytes={}",
                    requestId, System.currentTimeMillis() - materializeStartedAt, pdf.length);

            long validateStartedAt = System.currentTimeMillis();
            validatePdf(pdf);
            LOG.info("BAW-DOC-TIMING request={} stage=validate-pdf durationMs={} pdfBytes={}",
                    requestId, System.currentTimeMillis() - validateStartedAt, pdf.length);
            LOG.info("BAW-DOC-TIMING request={} stage=pdf-convert-complete durationMs={} pdfBytes={}",
                    requestId, System.currentTimeMillis() - startedAt, pdf.length);
            return pdf;
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
            LOG.info("BAW-DOC-TIMING request={} stage=pdf-convert-failed durationMs={} error={}",
                    requestId, System.currentTimeMillis() - startedAt, e.getClass().getSimpleName());
            LOG.error("DOCX to PDF conversion failed", e);
            throw new DocumentGenerationException("DOC-006", "DOCX to PDF conversion failed", e);
        }
    }

    private void validatePdf(byte[] pdf) throws DocumentGenerationException {
        if (pdf == null || pdf.length < 8) {
            throw new DocumentGenerationException("DOC-006", "PDF conversion produced an empty result");
        }
        String header = new String(pdf, 0, Math.min(pdf.length, 8), java.nio.charset.StandardCharsets.US_ASCII);
        if (!header.startsWith("%PDF-")) {
            throw new DocumentGenerationException("DOC-006", "PDF conversion result is not a valid PDF");
        }
    }
}
