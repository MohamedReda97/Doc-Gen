package com.bankaudi.baw.document.engine;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class WordParts {
    private WordParts() {
    }

    public static List<Part> jaxbParts(WordprocessingMLPackage wordPackage) {
        List<Part> parts = new ArrayList<>();
        for (Part part : wordPackage.getParts().getParts().values()) {
            // Include parts that have getJaxbElement() method
            if (hasJaxbElement(part)) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static boolean hasJaxbElement(Part part) {
        try {
            Method method = part.getClass().getMethod("getJaxbElement");
            return method != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static Object getJaxbElement(Part part) throws ReflectiveOperationException {
        return part.getClass().getMethod("getJaxbElement").invoke(part);
    }

    public static String getXML(Part part) throws ReflectiveOperationException {
        return (String) part.getClass().getMethod("getXML").invoke(part);
    }
}
