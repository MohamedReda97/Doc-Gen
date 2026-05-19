package com.bawdocgen;

import com.bawdocgen.api.DocxGenerationService;
import com.bawdocgen.api.DocumentGenerator;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BawPersonalLoanPayloadTest {
    @Test
    void bawPayloadMatchesBundledPersonalLoanTemplateAndGeneratesDocx() throws Exception {
        byte[] template = resourceBytes("/templates/personal-loan-application.docx");
        String json = new String(
                Files.readAllBytes(Paths.get("BAW_personal-loan-application-test-payload.json")),
                StandardCharsets.UTF_8);

        Set<String> placeholders = DocumentGenerator.getInstance().extractPlaceholders(template);
        Set<String> payloadKeys = flatMappingKeys(json);

        assertEquals(142, placeholders.size());
        assertEquals(placeholders, payloadKeys);

        String docxBase64 = new DocxGenerationService().generateDocxBase64("personal-loan-application", json);
        byte[] docx = Base64.getDecoder().decode(docxBase64);
        String documentXml = docxEntry(docx, "word/document.xml");

        assertTrue(docx.length > 1000);
        assertTrue(new String(docx, 0, 2, StandardCharsets.US_ASCII).startsWith("PK"));
        assertTrue(documentXml.contains("Karim"));
        assertTrue(!documentXml.contains("{{"));
    }

    private Set<String> flatMappingKeys(String json) throws Exception {
        JsonNode flatMapping = new ObjectMapper().readTree(json).get("flat_mapping");
        Set<String> keys = new LinkedHashSet<>();
        flatMapping.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private String docxEntry(byte[] docx, String entryName) throws Exception {
        try (ZipInputStream inputStream = new ZipInputStream(new java.io.ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    byte[] buffer = new byte[8192];
                    int read;
                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
                    }
                }
                inputStream.closeEntry();
            }
        }
        throw new IllegalArgumentException("Missing DOCX entry " + entryName);
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
