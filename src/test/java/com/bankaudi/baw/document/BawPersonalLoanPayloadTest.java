package com.bankaudi.baw.document;

import com.bankaudi.baw.document.api.BawDocumentService;
import com.bankaudi.baw.document.api.DocumentGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BawPersonalLoanPayloadTest {
    @Test
    void bawPayloadMatchesBundledPersonalLoanTemplateAndGeneratesPdf() throws Exception {
        byte[] template = resourceBytes("/templates/personal-loan-application.docx");
        String json = new String(
                Files.readAllBytes(Paths.get("BAW_personal-loan-application-test-payload.json")),
                StandardCharsets.UTF_8);

        Set<String> placeholders = DocumentGenerator.getInstance().extractPlaceholders(template);
        Set<String> payloadKeys = flatMappingKeys(json);

        assertEquals(142, placeholders.size());
        assertEquals(placeholders, payloadKeys);

        String pdfBase64 = new BawDocumentService().generatePdfBase64("personal-loan-application", json);
        byte[] pdf = Base64.getDecoder().decode(pdfBase64);

        assertTrue(pdf.length > 1000);
        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    private Set<String> flatMappingKeys(String json) throws Exception {
        JsonNode flatMapping = new ObjectMapper().readTree(json).get("flat_mapping");
        Set<String> keys = new LinkedHashSet<>();
        flatMapping.fieldNames().forEachRemaining(keys::add);
        return keys;
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
