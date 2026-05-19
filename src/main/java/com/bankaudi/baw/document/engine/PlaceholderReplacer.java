package com.bankaudi.baw.document.engine;

import com.bankaudi.baw.document.font.ScriptRunFormatter;
import javax.xml.bind.JAXBElement;
import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class PlaceholderReplacer {
    private final ObjectFactory objectFactory = new ObjectFactory();
    private final ScriptRunFormatter scriptRunFormatter = new ScriptRunFormatter();

    public int replace(WordprocessingMLPackage wordPackage, Map<String, String> values) {
        int count = 0;
        for (Part part : WordParts.jaxbParts(wordPackage)) {
            try {
                Object jaxbElement = WordParts.getJaxbElement(part);
                count += replaceInObject(jaxbElement, values);
            } catch (ReflectiveOperationException e) {
                // Skip parts that cannot be processed
            }
        }
        return count;
    }

    public int replaceInObject(Object root, Map<String, String> values) {
        ParagraphCollector paragraphCollector = new ParagraphCollector();
        new TraversalUtil(root, paragraphCollector);
        int count = 0;
        for (P paragraph : paragraphCollector.getParagraphs()) {
            count += replaceInParagraph(paragraph, values);
        }
        return count;
    }

    int replaceInParagraph(P paragraph, Map<String, String> values) {
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
        if (splitMixedScriptRunRange(first, last, match)) {
            return;
        }
        formatReplacementRun(first.node, match.replacement);
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

    private void formatReplacementRun(Text text, String replacement) {
        Object parent = text.getParent();
        if (parent instanceof R) {
            scriptRunFormatter.formatReplacement((R) parent, replacement);
        }
    }

    private boolean splitMixedScriptRunRange(Slice firstSlice, Slice lastSlice, Match match) {
        if (!scriptRunFormatter.containsLatin(match.replacement)
                || scriptRunFormatter.containsArabic(match.replacement)) {
            return false;
        }

        String firstText = firstSlice.node.getValue() == null ? "" : firstSlice.node.getValue();
        String lastText = lastSlice.node.getValue() == null ? "" : lastSlice.node.getValue();
        String before = firstText.substring(0, match.start - firstSlice.start);
        String after = lastText.substring(match.end - lastSlice.start);
        if (!scriptRunFormatter.containsArabic(before) && !scriptRunFormatter.containsArabic(after)) {
            return false;
        }

        Object firstRunObject = firstSlice.node.getParent();
        Object lastRunObject = lastSlice.node.getParent();
        if (!(firstRunObject instanceof R) || !(lastRunObject instanceof R)) {
            return false;
        }
        R firstRun = (R) firstRunObject;
        R lastRun = (R) lastRunObject;
        Object parentObject = firstRun.getParent();
        if (!(parentObject instanceof ContentAccessor) || parentObject != lastRun.getParent()) {
            return false;
        }

        List<Object> parentContent = ((ContentAccessor) parentObject).getContent();
        int firstRunIndex = indexOfRun(parentContent, firstRun);
        int lastRunIndex = indexOfRun(parentContent, lastRun);
        if (firstRunIndex < 0 || lastRunIndex < firstRunIndex) {
            return false;
        }

        List<Object> replacementRuns = new ArrayList<>();
        if (!before.isEmpty()) {
            replacementRuns.add(copyRunWithSingleText(firstRun, before));
        }

        R replacementRun = copyRunWithSingleText(firstRun, match.replacement);
        scriptRunFormatter.formatLatinReplacementInMixedScriptContext(replacementRun, match.replacement);
        replacementRuns.add(replacementRun);

        if (!after.isEmpty()) {
            replacementRuns.add(copyRunWithSingleText(lastRun, after));
        }

        for (int i = lastRunIndex; i >= firstRunIndex; i--) {
            parentContent.remove(i);
        }
        for (Object replacementRunObject : replacementRuns) {
            if (replacementRunObject instanceof R) {
                ((R) replacementRunObject).setParent(parentObject);
            }
        }
        parentContent.addAll(firstRunIndex, replacementRuns);
        return true;
    }

    private int indexOfRun(List<Object> content, R run) {
        for (int i = 0; i < content.size(); i++) {
            Object value = content.get(i);
            if (value instanceof JAXBElement) {
                value = ((JAXBElement<?>) value).getValue();
            }
            if (value == run) {
                return i;
            }
        }
        return -1;
    }

    private R copyRunWithSingleText(R originalRun, String value) {
        R copy = XmlUtils.deepCopy(originalRun);
        copy.getContent().clear();
        Text text = objectFactory.createText();
        text.setValue(value);
        text.setParent(copy);
        preserveSpaces(text);
        copy.getContent().add(text);
        return copy;
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
