package com.bankaudi.baw.document.engine;

import jakarta.xml.bind.JAXBElement;
import org.docx4j.TraversalUtil;
import org.docx4j.wml.Text;

import java.util.ArrayList;
import java.util.List;

class TextNodeCollector extends TraversalUtil.CallbackImpl {
    private final List<Text> texts = new ArrayList<>();

    @Override
    public List<Object> apply(Object object) {
        Object value = object;
        if (value instanceof JAXBElement) {
            value = ((JAXBElement<?>) value).getValue();
        }
        if (value instanceof Text) {
            texts.add((Text) value);
        }
        return null;
    }

    List<Text> getTexts() {
        return texts;
    }
}
