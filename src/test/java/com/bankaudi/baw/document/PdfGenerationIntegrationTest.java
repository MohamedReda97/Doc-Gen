package com.bankaudi.baw.document;

import com.bankaudi.baw.document.api.DocumentGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfGenerationIntegrationTest {
    @Test
    void generatesPdfFromBundledSampleTemplate() throws Exception {
        String json = resourceString("/Personal_Loan_Application_form_variables.json");

        byte[] pdf = DocumentGenerator.getInstance().generatePdf("personal-loan-application", json);

        assertTrue(pdf.length > 1000);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Karim"));
            assertTrue(text.contains("Nasser"));
        }
    }

    private String resourceString(String path) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource " + path);
            }
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
