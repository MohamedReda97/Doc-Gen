package com.bankaudi.baw.document.engine;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.wml.P;
import org.docx4j.wml.Text;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;

public class PlaceholderExtractor {
    public Set<String> extract(WordprocessingMLPackage wordPackage) {
        Set<String> placeholders = new LinkedHashSet<>();
        for (Part part : WordParts.jaxbParts(wordPackage)) {
            ParagraphCollector paragraphCollector = new ParagraphCollector();
            try {
                Object jaxbElement = WordParts.getJaxbElement(part);
                new org.docx4j.TraversalUtil(jaxbElement, paragraphCollector);
                for (P paragraph : paragraphCollector.getParagraphs()) {
                    String text = combinedText(paragraph);
                    Matcher matcher = PlaceholderPattern.TOKEN.matcher(text);
                    while (matcher.find()) {
                        placeholders.add(matcher.group(1));
                    }
                }
            } catch (ReflectiveOperationException e) {
                // Skip parts that cannot be processed
            }
        }
        return placeholders;
    }

    private String combinedText(Object root) {
        TextNodeCollector collector = new TextNodeCollector();
        new org.docx4j.TraversalUtil(root, collector);
        StringBuilder builder = new StringBuilder();
        for (Text text : collector.getTexts()) {
            if (text.getValue() != null) {
                builder.append(text.getValue());
            }
        }
        return builder.toString();
    }
}
