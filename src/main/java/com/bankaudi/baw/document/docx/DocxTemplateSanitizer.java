package com.bankaudi.baw.document.docx;

import com.bankaudi.baw.document.api.DocumentGenerationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DocxTemplateSanitizer {
    private static final Pattern DECIMAL_WORD_ATTRIBUTE =
            Pattern.compile("(\\bw:[A-Za-z0-9]+=\")(-?\\d+)\\.(\\d+)(\")");
    private static final Pattern SHORT_LANGUAGE =
            Pattern.compile("(<w:lang\\b[^>]*\\bw:val=\")([A-Za-z]{2})(\")");

    public byte[] sanitize(byte[] docxTemplateBytes) throws DocumentGenerationException {
        try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(docxTemplateBytes));
             ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream(docxTemplateBytes.length);
             ZipOutputStream outputStream = new ZipOutputStream(byteOutputStream)) {

            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                ZipEntry sanitizedEntry = new ZipEntry(entry.getName());
                sanitizedEntry.setTime(entry.getTime());
                outputStream.putNextEntry(sanitizedEntry);

                byte[] content = readAllBytes(inputStream);
                if (shouldSanitize(entry.getName())) {
                    content = sanitizeXml(new String(content, StandardCharsets.UTF_8))
                            .getBytes(StandardCharsets.UTF_8);
                }
                outputStream.write(content);
                outputStream.closeEntry();
                inputStream.closeEntry();
            }
            outputStream.finish();
            return byteOutputStream.toByteArray();
        } catch (IOException e) {
            throw new DocumentGenerationException("DOC-002", "Failed to sanitize DOCX template", e);
        }
    }

    private boolean shouldSanitize(String entryName) {
        return entryName != null && entryName.startsWith("word/") && entryName.endsWith(".xml");
    }

    private String sanitizeXml(String xml) {
        String sanitized = normalizeDecimalWordAttributes(xml);
        return normalizeShortLanguageTags(sanitized);
    }

    private String normalizeDecimalWordAttributes(String xml) {
        Matcher matcher = DECIMAL_WORD_ATTRIBUTE.matcher(xml);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String normalized = matcher.group(1) + roundedInteger(matcher.group(2), matcher.group(3)) + matcher.group(4);
            matcher.appendReplacement(output, Matcher.quoteReplacement(normalized));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String roundedInteger(String wholePart, String fractionPart) {
        double value = Double.parseDouble(wholePart + "." + fractionPart);
        return Long.toString(Math.round(value));
    }

    private String normalizeShortLanguageTags(String xml) {
        Matcher matcher = SHORT_LANGUAGE.matcher(xml);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String language = matcher.group(2).toLowerCase(Locale.ROOT);
            String normalized = matcher.group(1) + defaultRegion(language) + matcher.group(3);
            matcher.appendReplacement(output, Matcher.quoteReplacement(normalized));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String defaultRegion(String language) {
        if ("ar".equals(language)) {
            return "ar-SA";
        }
        if ("fr".equals(language)) {
            return "fr-FR";
        }
        return language + "-US";
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
