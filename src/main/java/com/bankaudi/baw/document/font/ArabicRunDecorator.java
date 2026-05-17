package com.bankaudi.baw.document.font;

import jakarta.xml.bind.JAXBElement;
import org.docx4j.TraversalUtil;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.CTLanguage;
import org.docx4j.wml.R;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Text;

import java.util.List;

public class ArabicRunDecorator {
    private final ScriptRunFormatter scriptRunFormatter = new ScriptRunFormatter();

    public void decorate(WordprocessingMLPackage wordPackage) {
        for (Part part : com.bankaudi.baw.document.engine.WordParts.jaxbParts(wordPackage)) {
            try {
                Object jaxbElement = com.bankaudi.baw.document.engine.WordParts.getJaxbElement(part);
                new TraversalUtil(jaxbElement, new TraversalUtil.CallbackImpl() {
                    @Override
                    public List<Object> apply(Object object) {
                        Object value = object instanceof JAXBElement ? ((JAXBElement<?>) object).getValue() : object;
                        if (value instanceof R && runContainsArabic((R) value)) {
                            applyArabicProperties((R) value);
                        }
                        return null;
                    }
                });
            } catch (ReflectiveOperationException e) {
                // Skip parts that cannot be processed
            }
        }
    }

    private boolean runContainsArabic(R run) {
        TextCollector collector = new TextCollector();
        new TraversalUtil(run, collector);
        for (String text : collector.values) {
            if (scriptRunFormatter.containsArabic(text)) {
                return true;
            }
        }
        return false;
    }

    private void applyArabicProperties(R run) {
        RPr rPr = run.getRPr();
        if (rPr == null) {
            rPr = Context.getWmlObjectFactory().createRPr();
            run.setRPr(rPr);
        }

        RFonts fonts = rPr.getRFonts();
        if (fonts == null) {
            fonts = Context.getWmlObjectFactory().createRFonts();
            rPr.setRFonts(fonts);
        }
        fonts.setCs("Noto Naskh Arabic");

        CTLanguage language = rPr.getLang();
        if (language == null) {
            language = Context.getWmlObjectFactory().createCTLanguage();
            rPr.setLang(language);
        }
        language.setVal("ar-SA");
        language.setBidi("ar-SA");

        BooleanDefaultTrue rtl = new BooleanDefaultTrue();
        rtl.setVal(Boolean.TRUE);
        rPr.setRtl(rtl);
    }

    private static class TextCollector extends TraversalUtil.CallbackImpl {
        private final java.util.List<String> values = new java.util.ArrayList<>();

        @Override
        public List<Object> apply(Object object) {
            Object value = object instanceof JAXBElement ? ((JAXBElement<?>) object).getValue() : object;
            if (value instanceof Text) {
                values.add(((Text) value).getValue());
            }
            return null;
        }
    }
}
