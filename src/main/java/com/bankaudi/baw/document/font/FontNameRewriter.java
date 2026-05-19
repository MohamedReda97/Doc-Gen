package com.bankaudi.baw.document.font;

import javax.xml.bind.JAXBElement;
import org.docx4j.TraversalUtil;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.wml.RFonts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FontNameRewriter {
    private static final Map<String, String> FONT_MAP = new LinkedHashMap<>();

    static {
        FONT_MAP.put("Calibri", "Liberation Sans");
        FONT_MAP.put("Arial", "Noto Sans");
        FONT_MAP.put("Times New Roman", "Liberation Serif");
        FONT_MAP.put("Cambria", "Liberation Serif");
        FONT_MAP.put("Segoe UI Symbol", "Noto Sans Symbols");
        FONT_MAP.put("Symbol", "Noto Sans Symbols");
        FONT_MAP.put("Wingdings", "Noto Sans Symbols");
    }

    public void rewrite(WordprocessingMLPackage wordPackage) {
        for (Part part : com.bankaudi.baw.document.engine.WordParts.jaxbParts(wordPackage)) {
            try {
                Object jaxbElement = com.bankaudi.baw.document.engine.WordParts.getJaxbElement(part);
                new TraversalUtil(jaxbElement, new TraversalUtil.CallbackImpl() {
                    @Override
                    public List<Object> apply(Object object) {
                        Object value = object instanceof JAXBElement ? ((JAXBElement<?>) object).getValue() : object;
                        if (value instanceof RFonts) {
                            rewriteFonts((RFonts) value);
                        }
                        return null;
                    }
                });
            } catch (ReflectiveOperationException e) {
                // Skip parts that cannot be processed.
            }
        }
    }

    private void rewriteFonts(RFonts fonts) {
        fonts.setAscii(mapped(fonts.getAscii()));
        fonts.setHAnsi(mapped(fonts.getHAnsi()));
        fonts.setCs(mapped(fonts.getCs()));
        fonts.setEastAsia(mapped(fonts.getEastAsia()));
    }

    private String mapped(String fontName) {
        if (fontName == null) {
            return null;
        }
        return FONT_MAP.getOrDefault(fontName, fontName);
    }
}
