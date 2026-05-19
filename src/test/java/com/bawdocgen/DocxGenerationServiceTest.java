package com.bawdocgen;

import com.bawdocgen.api.DocxGenerationService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxGenerationServiceTest {
    @Test
    void generatesBase64DocxForBawSupportedSignature() throws Exception {
        String json = resourceString("/Personal_Loan_Application_form_variables.json");

        String docxBase64 = new DocxGenerationService().generateDocxBase64("personal-loan-application", json);
        byte[] docx = Base64.getDecoder().decode(docxBase64);

        assertTrue(docx.length > 1000);
        assertTrue(new String(docx, 0, 2, StandardCharsets.US_ASCII).startsWith("PK"));
    }

    @Test
    void throwsHelpfulErrorWhenTemplateIsMissing() {
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                new DocxGenerationService().generateDocxBase64("missing-template", "{\"flat_mapping\":{}}"));

        assertTrue(error.getMessage().contains("BAW DOCX generation failed"));
        assertTrue(error.getMessage().contains("code=DOC-001"));
        assertTrue(error.getMessage().contains("templateName=missing-template"));
        assertTrue(error.getMessage().contains("Bundled template not found"));
    }

    @Test
    void throwsHelpfulErrorWhenJsonIsInvalid() {
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                new DocxGenerationService().generateDocxBase64("personal-loan-application", "{not-json"));

        assertTrue(error.getMessage().contains("BAW DOCX generation failed"));
        assertTrue(error.getMessage().contains("code=DOC-004"));
        assertTrue(error.getMessage().contains("templateName=personal-loan-application"));
        assertTrue(error.getMessage().contains("jsonPreview={not-json"));
        assertTrue(error.getMessage().contains("rootCause="));
    }

    private String resourceString(String path) throws Exception {
        return new String(resourceBytes(path), StandardCharsets.UTF_8);
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
