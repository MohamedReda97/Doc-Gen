package com.bankaudi.baw.document.validation;

import com.bankaudi.baw.document.engine.PlaceholderExtractor;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;

import java.util.LinkedHashSet;
import java.util.Set;

public class TemplateValidator {
    private final PlaceholderExtractor placeholderExtractor = new PlaceholderExtractor();

    public ValidationReport validate(WordprocessingMLPackage wordPackage) {
        Set<String> warnings = new LinkedHashSet<>();
        Set<String> errors = new LinkedHashSet<>();
        Set<String> placeholders = placeholderExtractor.extract(wordPackage);

        for (Part part : com.bankaudi.baw.document.engine.WordParts.jaxbParts(wordPackage)) {
            String xml = xml(part);
            if (xml.contains("txbxContent")) {
                warnings.add("Template contains text boxes; docx4j/FOP rendering may differ from Word.");
            }
            if (xml.contains("<w:drawing") || xml.contains("<w:pict")) {
                warnings.add("Template contains drawings or legacy pictures; verify PDF visually.");
            }
            if (xml.contains("<w:fldSimple") || xml.contains("<w:instrText")) {
                warnings.add("Template contains Word fields; verify PDF visually.");
            }
        }

        if (placeholders.isEmpty()) {
            warnings.add("Template does not contain any {{placeholder}} tokens.");
        }
        return new ValidationReport(placeholders, warnings, errors);
    }

    private String xml(Part part) {
        try {
            return com.bankaudi.baw.document.engine.WordParts.getXML(part);
        } catch (Exception e) {
            return "";
        }
    }
}
