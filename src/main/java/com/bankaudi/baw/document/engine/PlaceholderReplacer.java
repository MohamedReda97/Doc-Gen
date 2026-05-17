package com.bankaudi.baw.document.engine;

import org.docx4j.TraversalUtil;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.wml.P;
import org.docx4j.wml.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class PlaceholderReplacer {
    public int replace(WordprocessingMLPackage wordPackage, Map<String, String> values) {
        int count = 0;
        for (Part part : WordParts.jaxbParts(wordPackage)) {
            ParagraphCollector paragraphCollector = new ParagraphCollector();
            try {
                Object jaxbElement = WordParts.getJaxbElement(part);
                new TraversalUtil(jaxbElement, paragraphCollector);
                for (P paragraph : paragraphCollector.getParagraphs()) {
                    count += replaceInParagraph(paragraph, values);
                }
            } catch (ReflectiveOperationException e) {
                // Skip parts that cannot be processed
            }
        }
        return count;
    }

    private int replaceInParagraph(P paragraph, Map<String, String> values) {
        TextNodeCollector textCollector = new TextNodeCollector();
        new TraversalUtil(paragraph, textCollector);
        List<Text> nodes = textCollector.getTexts();
        if (nodes.isEmpty()) {
            return 0;
        }

        StringBuilder fullText = new StringBuilder();
        List<Slice> slices = new ArrayList<>();
        for (Text node : nodes) {
            String value = node.getValue() == null ? "" : node.getValue();
            int start = fullText.length();
            fullText.append(value);
            slices.add(new Slice(node, start, fullText.length()));
        }

        Matcher matcher = PlaceholderPattern.TOKEN.matcher(fullText);
        List<Match> matches = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            matches.add(new Match(matcher.start(), matcher.end(), values.getOrDefault(key, "")));
        }

        for (int i = matches.size() - 1; i >= 0; i--) {
            applyMatch(matches.get(i), slices);
        }
        return matches.size();
    }

    private void applyMatch(Match match, List<Slice> slices) {
        int firstIndex = findSlice(slices, match.start);
        int lastIndex = findSlice(slices, match.end - 1);
        if (firstIndex < 0 || lastIndex < 0) {
            return;
        }

        Slice first = slices.get(firstIndex);
        Slice last = slices.get(lastIndex);
        String firstText = first.node.getValue() == null ? "" : first.node.getValue();
        String before = firstText.substring(0, match.start - first.start);

        if (firstIndex == lastIndex) {
            String after = firstText.substring(match.end - first.start);
            first.node.setValue(before + match.replacement + after);
            preserveSpaces(first.node);
            return;
        }

        String lastText = last.node.getValue() == null ? "" : last.node.getValue();
        String after = lastText.substring(match.end - last.start);
        first.node.setValue(before + match.replacement);
        preserveSpaces(first.node);
        for (int i = firstIndex + 1; i < lastIndex; i++) {
            slices.get(i).node.setValue("");
        }
        last.node.setValue(after);
        preserveSpaces(last.node);
    }

    private int findSlice(List<Slice> slices, int position) {
        for (int i = 0; i < slices.size(); i++) {
            Slice slice = slices.get(i);
            if (position >= slice.start && position < slice.end) {
                return i;
            }
        }
        return -1;
    }

    private void preserveSpaces(Text text) {
        String value = text.getValue();
        if (value != null && (!value.equals(value.trim()) || value.contains("  "))) {
            text.setSpace("preserve");
        }
    }

    private static final class Slice {
        private final Text node;
        private final int start;
        private final int end;

        private Slice(Text node, int start, int end) {
            this.node = node;
            this.start = start;
            this.end = end;
        }
    }

    private static final class Match {
        private final int start;
        private final int end;
        private final String replacement;

        private Match(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement == null ? "" : replacement;
        }
    }
}
