package com.bawdocgen.docx;

import com.bawdocgen.api.DocumentGenerationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DocxPackageProcessor {
    private final DocxTemplateSanitizer sanitizer = new DocxTemplateSanitizer();
    private final XmlPartTransformer xmlPartTransformer = new XmlPartTransformer();

    public byte[] generate(byte[] templateBytes, Map<String, String> values,
                           Map<String, List<Map<String, String>>> tables) throws DocumentGenerationException {
        byte[] sanitizedTemplateBytes = sanitizer.sanitize(templateBytes);
        try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(sanitizedTemplateBytes));
             ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream(sanitizedTemplateBytes.length);
             ZipOutputStream outputStream = new ZipOutputStream(byteOutputStream)) {

            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                ZipEntry outputEntry = new ZipEntry(entry.getName());
                outputEntry.setTime(entry.getTime());
                outputStream.putNextEntry(outputEntry);

                byte[] content = readAllBytes(inputStream);
                if (isTransformableWordXmlPart(entry.getName())) {
                    content = xmlPartTransformer.transform(content, values, tables);
                }
                outputStream.write(content);
                outputStream.closeEntry();
                inputStream.closeEntry();
            }
            outputStream.finish();
            return byteOutputStream.toByteArray();
        } catch (IOException e) {
            throw new DocumentGenerationException("DOC-002", "Failed to generate DOCX package", e);
        }
    }

    public Set<String> extractPlaceholders(byte[] templateBytes) throws DocumentGenerationException {
        byte[] sanitizedTemplateBytes = sanitizer.sanitize(templateBytes);
        Set<String> placeholders = new LinkedHashSet<>();
        try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(sanitizedTemplateBytes))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                byte[] content = readAllBytes(inputStream);
                if (isTransformableWordXmlPart(entry.getName())) {
                    placeholders.addAll(xmlPartTransformer.extractPlaceholders(content));
                }
                inputStream.closeEntry();
            }
            return placeholders;
        } catch (IOException e) {
            throw new DocumentGenerationException("DOC-002", "Failed to inspect DOCX package", e);
        }
    }

    private boolean isTransformableWordXmlPart(String entryName) {
        if (entryName == null || !entryName.startsWith("word/") || !entryName.endsWith(".xml")) {
            return false;
        }
        return "word/document.xml".equals(entryName)
                || entryName.matches("word/header\\d+\\.xml")
                || entryName.matches("word/footer\\d+\\.xml")
                || "word/footnotes.xml".equals(entryName)
                || "word/endnotes.xml".equals(entryName)
                || "word/comments.xml".equals(entryName);
    }

    private byte[] readAllBytes(ZipInputStream inputStream) throws IOException {
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
