package com.bankaudi.baw.document;

import com.bankaudi.baw.document.api.BawDocumentService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BawDocumentServiceTest {
    @Test
    void generatesBase64PdfForBawSupportedSignature() throws Exception {
        String json = resourceString("/Personal_Loan_Application_form_variables.json");

        String pdfBase64 = new BawDocumentService().generatePdfBase64("personal-loan-application", json);
        byte[] pdf = Base64.getDecoder().decode(pdfBase64);

        assertTrue(pdf.length > 1000);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    @Test
    void throwsHelpfulErrorWhenTemplateIsMissing() {
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                new BawDocumentService().generatePdfBase64("missing-template", "{\"flat_mapping\":{}}"));

        assertTrue(error.getMessage().contains("BAW document generation failed"));
        assertTrue(error.getMessage().contains("code=DOC-001"));
        assertTrue(error.getMessage().contains("templateName=missing-template"));
        assertTrue(error.getMessage().contains("Bundled template not found"));
    }

    @Test
    void throwsHelpfulErrorWhenJsonIsInvalid() {
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                new BawDocumentService().generatePdfBase64("personal-loan-application", "{not-json"));

        assertTrue(error.getMessage().contains("BAW document generation failed"));
        assertTrue(error.getMessage().contains("code=DOC-004"));
        assertTrue(error.getMessage().contains("templateName=personal-loan-application"));
        assertTrue(error.getMessage().contains("jsonPreview={not-json"));
        assertTrue(error.getMessage().contains("rootCause="));
    }

    private String resourceString(String path) throws Exception {
        return new String(resourceBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] resourceBytes(String path) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource " + path);
            }
            byte[] buffer = new byte[8192];
            int read;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                return outputStream.toByteArray();
            }
        }
    }
}
