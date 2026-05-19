package com.bawdocgen;

import com.bawdocgen.docx.DocxPackageProcessor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxPackageProcessorTest {
    @Test
    void replacesPlaceholdersSplitAcrossWordRuns() throws Exception {
        byte[] docx = minimalDocx("<w:p><w:r><w:t>{{cust</w:t></w:r><w:r><w:t>omer.name}}</w:t></w:r></w:p>");
        Map<String, String> values = Collections.singletonMap("customer.name", "Maya");

        byte[] generated = new DocxPackageProcessor().generate(docx, values, Collections.emptyMap());
        String documentXml = docxEntry(generated, "word/document.xml");

        assertTrue(documentXml.contains("Maya"));
        assertFalse(documentXml.contains("{{customer.name}}"));
    }

    @Test
    void repeatsDynamicTableRows() throws Exception {
        byte[] docx = minimalDocx("<w:tbl><w:tr><w:tc><w:p><w:r><w:t>{{items[].name}}</w:t></w:r></w:p></w:tc></w:tr></w:tbl>");
        Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
        tables.put("items", Arrays.asList(row("name", "First"), row("name", "Second")));

        byte[] generated = new DocxPackageProcessor().generate(docx, Collections.emptyMap(), tables);
        String documentXml = docxEntry(generated, "word/document.xml");

        assertTrue(documentXml.contains("First"));
        assertTrue(documentXml.contains("Second"));
        assertFalse(documentXml.contains("{{items[].name}}"));
    }

    private Map<String, String> row(String key, String value) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put(key, value);
        return row;
    }

    private byte[] minimalDocx(String body) throws Exception {
        String documentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body + "</w:body></w:document>";
        try (ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(byteOutputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zipOutputStream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types/>".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry("word/document.xml"));
            zipOutputStream.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return byteOutputStream.toByteArray();
        }
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
}
