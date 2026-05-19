package com.bawdocgen.docx;

import com.bawdocgen.api.DocumentGenerationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class XmlPartTransformer {
    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\[\\]-]+)\\s*}}");
    private static final Pattern TABLE_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\[\\]\\.([A-Za-z0-9_.-]+)\\s*}}");

    byte[] transform(byte[] xmlBytes, Map<String, String> values,
                     Map<String, List<Map<String, String>>> tables) throws DocumentGenerationException {
        Document document = parse(xmlBytes);
        repeatTableRows(document, tables);
        replaceFlatPlaceholders(document, values);
        return serialize(document);
    }

    Set<String> extractPlaceholders(byte[] xmlBytes) throws DocumentGenerationException {
        Document document = parse(xmlBytes);
        String text = combinedText(document.getDocumentElement());
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    private void repeatTableRows(Document document, Map<String, List<Map<String, String>>> tables) {
        List<Element> rows = elementsByLocalName(document, "tr");
        for (Element row : rows) {
            String rowText = combinedText(row);
            Matcher matcher = TABLE_PLACEHOLDER.matcher(rowText);
            if (!matcher.find()) {
                continue;
            }

            String tableName = matcher.group(1);
            List<Map<String, String>> tableRows = tables.get(tableName);
            Node parent = row.getParentNode();
            if (parent == null) {
                continue;
            }

            if (tableRows != null) {
                for (Map<String, String> tableRow : tableRows) {
                    Node clone = row.cloneNode(true);
                    replaceTextNodes(clone, rowScopedValues(tableName, tableRow));
                    parent.insertBefore(clone, row);
                }
            }
            parent.removeChild(row);
        }
    }

    private Map<String, String> rowScopedValues(String tableName, Map<String, String> tableRow) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : tableRow.entrySet()) {
            values.put(tableName + "[]." + entry.getKey(), entry.getValue());
        }
        return values;
    }

    private void replaceFlatPlaceholders(Document document, Map<String, String> values) {
        List<Element> paragraphs = elementsByLocalName(document, "p");
        for (Element paragraph : paragraphs) {
            replaceTextNodes(paragraph, values);
        }
    }

    private void replaceTextNodes(Node container, Map<String, String> values) {
        List<Node> textNodes = wordTextNodes(container);
        if (textNodes.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (Node textNode : textNodes) {
            String original = textNode.getTextContent();
            String replaced = replacePlaceholders(original, values);
            if (!original.equals(replaced)) {
                textNode.setTextContent(replaced);
                changed = true;
            }
        }

        String combined = combinedTextFromNodes(textNodes);
        Matcher unresolved = PLACEHOLDER.matcher(combined);
        if (!unresolved.find()) {
            return;
        }

        String replacedCombined = replacePlaceholders(combined, values);
        if (!combined.equals(replacedCombined) || !changed) {
            textNodes.get(0).setTextContent(replacedCombined);
            for (int i = 1; i < textNodes.size(); i++) {
                textNodes.get(i).setTextContent("");
            }
        }
    }

    private String replacePlaceholders(String text, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.get(key);
            if (replacement == null) {
                replacement = "";
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Document parse(byte[] xmlBytes) throws DocumentGenerationException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource source = new InputSource(new StringReader(new String(xmlBytes, StandardCharsets.UTF_8)));
            return builder.parse(source);
        } catch (Exception e) {
            throw new DocumentGenerationException("DOC-002", "Failed to parse DOCX XML part", e);
        }
    }

    private void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean enabled)
            throws ParserConfigurationException {
        try {
            factory.setFeature(feature, enabled);
        } catch (ParserConfigurationException e) {
            if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(feature)) {
                throw e;
            }
        }
    }

    private byte[] serialize(Document document) throws DocumentGenerationException {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                transformer.transform(new DOMSource(document), new StreamResult(outputStream));
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            throw new DocumentGenerationException("DOC-002", "Failed to serialize DOCX XML part", e);
        }
    }

    private List<Element> elementsByLocalName(Document document, String localName) {
        NodeList nodeList = document.getElementsByTagNameNS(WORD_NS, localName);
        List<Element> elements = new ArrayList<>(nodeList.getLength());
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    private List<Node> wordTextNodes(Node container) {
        List<Node> textNodes = new ArrayList<>();
        collectWordTextNodes(container, textNodes);
        return textNodes;
    }

    private void collectWordTextNodes(Node node, List<Node> textNodes) {
        if (node == null) {
            return;
        }
        if (isWordTextNode(node)) {
            textNodes.add(node);
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectWordTextNodes(children.item(i), textNodes);
        }
    }

    private boolean isWordTextNode(Node node) {
        return node instanceof Element
                && "t".equals(node.getLocalName())
                && WORD_NS.equals(node.getNamespaceURI());
    }

    private String combinedText(Node container) {
        return combinedTextFromNodes(wordTextNodes(container));
    }

    private String combinedTextFromNodes(List<Node> textNodes) {
        StringBuilder text = new StringBuilder();
        for (Node textNode : textNodes) {
            text.append(textNode.getTextContent());
        }
        return text.toString();
    }
}
