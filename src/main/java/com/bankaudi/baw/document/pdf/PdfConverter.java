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
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(128 * 1024)) {
            FOSettings settings = Docx4J.createFOSettings();
            settings.setOpcPackage(wordPackage);
            settings.setApacheFopMime("application/pdf");

            Docx4J.toFO(settings, outputStream, Docx4J.FLAG_EXPORT_PREFER_XSL);
            byte[] pdf = outputStream.toByteArray();
            validatePdf(pdf);
            return pdf;
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
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
