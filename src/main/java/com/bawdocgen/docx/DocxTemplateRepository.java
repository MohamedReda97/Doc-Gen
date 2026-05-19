package com.bawdocgen.docx;

import com.bawdocgen.api.DocumentGenerationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DocxTemplateRepository {
    private final ConcurrentMap<String, byte[]> templateCache = new ConcurrentHashMap<>();

    public byte[] getTemplate(String templateId) throws DocumentGenerationException {
        try {
            byte[] cached = templateCache.computeIfAbsent(templateId, this::loadTemplateUnchecked);
            return cached.clone();
        } catch (TemplateLoadRuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DocumentGenerationException) {
                throw (DocumentGenerationException) cause;
            }
            throw new DocumentGenerationException("DOC-001", "Failed to load bundled template: " + templateId, e);
        }
    }

    private byte[] loadTemplateUnchecked(String templateId) {
        try {
            return loadTemplate(templateId);
        } catch (DocumentGenerationException e) {
            throw new TemplateLoadRuntimeException(e);
        }
    }

    private byte[] loadTemplate(String templateId) throws DocumentGenerationException {
        String path = "/templates/" + templateId + ".docx";
        try (InputStream inputStream = DocxTemplateRepository.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new DocumentGenerationException("DOC-001", "Bundled template not found: " + templateId);
            }
            return readAllBytes(inputStream);
        } catch (IOException e) {
            throw new DocumentGenerationException("DOC-001", "Failed to read bundled template: " + templateId, e);
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static class TemplateLoadRuntimeException extends RuntimeException {
        TemplateLoadRuntimeException(DocumentGenerationException cause) {
            super(cause);
        }
    }
}
