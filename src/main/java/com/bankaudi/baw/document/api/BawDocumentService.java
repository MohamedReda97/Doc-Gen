package com.bankaudi.baw.document.api;

import java.util.Base64;

/**
 * BAW-friendly facade with Java Integration Service supported signatures.
 */
public class BawDocumentService {
    private static final int JSON_PREVIEW_LENGTH = 300;

    public BawDocumentService() {
    }

    public String generatePdfBase64(String templateName, String jsonPayload) {
        try {
            byte[] pdf = DocumentGenerator.getInstance().generatePdf(templateName, jsonPayload);
            return Base64.getEncoder().encodeToString(pdf);
        } catch (DocumentGenerationException e) {
            throw new BawDocumentServiceException(formatError(templateName, jsonPayload, e), e);
        } catch (RuntimeException e) {
            throw new BawDocumentServiceException(formatUnexpectedError(templateName, jsonPayload, e), e);
        }
    }

    private String formatError(String templateName, String jsonPayload, DocumentGenerationException e) {
        return "BAW document generation failed"
                + " | code=" + valueOrEmpty(e.getErrorCode())
                + " | message=" + valueOrEmpty(e.getMessage())
                + " | templateName=" + valueOrEmpty(templateName)
                + " | jsonPreview=" + preview(jsonPayload)
                + " | rootCause=" + rootCauseSummary(e);
    }

    private String formatUnexpectedError(String templateName, String jsonPayload, RuntimeException e) {
        return "BAW document generation failed"
                + " | code=DOC-999"
                + " | message=Unexpected runtime error"
                + " | templateName=" + valueOrEmpty(templateName)
                + " | jsonPreview=" + preview(jsonPayload)
                + " | rootCause=" + rootCauseSummary(e);
    }

    private String rootCauseSummary(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + valueOrEmpty(root.getMessage());
    }

    private String preview(String value) {
        String normalized = valueOrEmpty(value).replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= JSON_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, JSON_PREVIEW_LENGTH) + "...";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public static class BawDocumentServiceException extends RuntimeException {
        public BawDocumentServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
