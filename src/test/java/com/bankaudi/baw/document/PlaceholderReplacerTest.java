package com.bankaudi.baw.document;

import com.bankaudi.baw.document.engine.PlaceholderExtractor;
import com.bankaudi.baw.document.engine.PlaceholderReplacer;
import jakarta.xml.bind.JAXBElement;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderReplacerTest {
    @Test
    void replacesPlaceholderSplitAcrossRuns() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ObjectFactory factory = org.docx4j.jaxb.Context.getWmlObjectFactory();
        P paragraph = factory.createP();
        paragraph.getContent().add(run(factory, "First: {{personal."));
        paragraph.getContent().add(run(factory, "first_name}}"));
        pkg.getMainDocumentPart().getContent().add(paragraph);

        int count = new PlaceholderReplacer().replace(pkg, Collections.singletonMap("personal.first_name", "Karim"));

        assertEquals(1, count);
        assertTrue(new PlaceholderExtractor().extract(pkg).isEmpty());
        assertEquals("First: Karim", firstRunText(paragraph));
    }

    @Test
    void missingValueBecomesEmptyText() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ObjectFactory factory = org.docx4j.jaxb.Context.getWmlObjectFactory();
        P paragraph = factory.createP();
        paragraph.getContent().add(run(factory, "Name: {{missing.value}}."));
        pkg.getMainDocumentPart().getContent().add(paragraph);

        int count = new PlaceholderReplacer().replace(pkg, Collections.emptyMap());

        assertEquals(1, count);
        assertEquals("Name: .", firstRunText(paragraph));
    }

    @Test
    void replacesMixedArabicEnglishValue() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ObjectFactory factory = org.docx4j.jaxb.Context.getWmlObjectFactory();
        P paragraph = factory.createP();
        paragraph.getContent().add(run(factory, "Customer: {{customer.name}}"));
        pkg.getMainDocumentPart().getContent().add(paragraph);

        int count = new PlaceholderReplacer().replace(pkg, Collections.singletonMap("customer.name", "أحمد Mohamed"));

        assertEquals(1, count);
        assertEquals("Customer: أحمد Mohamed", firstRunText(paragraph));
    }


    private R run(ObjectFactory factory, String value) {
        R run = factory.createR();
        Text text = factory.createText();
        text.setValue(value);
        run.getContent().add(text);
        return run;
    }

    private String firstRunText(P paragraph) {
        Object runObject = unwrap(paragraph.getContent().get(0));
        Object textObject = unwrap(((R) runObject).getContent().get(0));
        return ((Text) textObject).getValue();
    }

    private Object unwrap(Object value) {
        return value instanceof JAXBElement ? ((JAXBElement<?>) value).getValue() : value;
    }
}
