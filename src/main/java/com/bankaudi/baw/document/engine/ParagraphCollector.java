package com.bankaudi.baw.document.engine;

import jakarta.xml.bind.JAXBElement;
import org.docx4j.TraversalUtil;
import org.docx4j.wml.P;

import java.util.ArrayList;
import java.util.List;

class ParagraphCollector extends TraversalUtil.CallbackImpl {
    private final List<P> paragraphs = new ArrayList<>();

    @Override
    public List<Object> apply(Object object) {
        Object value = object;
        if (value instanceof JAXBElement) {
            value = ((JAXBElement<?>) value).getValue();
        }
        if (value instanceof P) {
            paragraphs.add((P) value);
        }
        return null;
    }

    List<P> getParagraphs() {
        return paragraphs;
    }
}
