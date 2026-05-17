package com.bankaudi.baw.document.api;

public final class DocumentGeneratorFactory {
    private DocumentGeneratorFactory() {
    }

    public static DocumentGenerator getGenerator() {
        return DocumentGenerator.getInstance();
    }
}
