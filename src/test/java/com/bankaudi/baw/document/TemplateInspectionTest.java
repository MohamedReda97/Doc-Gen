package com.bankaudi.baw.document;

import com.bankaudi.baw.document.api.DocumentGenerator;
import com.bankaudi.baw.document.validation.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateInspectionTest {
    @Test
    void sampleTemplatePlaceholdersMatchSampleFlatMapping() throws Exception {
        byte[] template = resourceBytes("/Personal_Loan_Application_form_template(1).docx");
        String json = resourceString("/Personal_Loan_Application_form_variables.json");

        Set<String> placeholders = DocumentGenerator.getInstance().extractPlaceholders(template);
        JsonNode flatMapping = new ObjectMapper().readTree(json).get("flat_mapping");
        Set<String> keys = new HashSet<>();
        flatMapping.fieldNames().forEachRemaining(keys::add);

        assertEquals(142, placeholders.size());
        assertTrue(keys.containsAll(placeholders), "sample JSON must contain every template placeholder");
    }

    @Test
    void sampleTemplateHasNoFatalValidationErrors() throws Exception {
        byte[] template = resourceBytes("/Personal_Loan_Application_form_template(1).docx");

        ValidationReport report = DocumentGenerator.getInstance().validateTemplate(template);

        assertTrue(report.isValid());
        assertEquals(142, report.getPlaceholders().size());
    }

    private byte[] resourceBytes(String path) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource " + path);
            }
            return readAllBytes(inputStream);
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private String resourceString(String path) throws Exception {
        return new String(resourceBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }
}
