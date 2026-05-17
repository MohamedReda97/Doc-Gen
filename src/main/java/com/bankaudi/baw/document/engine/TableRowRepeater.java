package com.bankaudi.baw.document.engine;

import jakarta.xml.bind.JAXBElement;
import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TableRowRepeater {
    private static final Pattern TABLE_PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\[\\]\\.([A-Za-z0-9_.-]+)\\s*}}");

    private final PlaceholderReplacer placeholderReplacer = new PlaceholderReplacer();

    public int repeat(WordprocessingMLPackage wordPackage, Map<String, List<Map<String, String>>> tables) {
        if (tables == null || tables.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Part part : WordParts.jaxbParts(wordPackage)) {
            try {
                Object jaxbElement = WordParts.getJaxbElement(part);
                count += repeatInObject(jaxbElement, tables);
            } catch (ReflectiveOperationException e) {
                // Skip parts that cannot be processed.
            }
        }
        return count;
    }

    private int repeatInObject(Object root, Map<String, List<Map<String, String>>> tables) {
        TableCollector tableCollector = new TableCollector();
        new TraversalUtil(root, tableCollector);
        int count = 0;
        for (Tbl table : tableCollector.tables) {
            count += repeatInTable(table, tables);
        }
        return count;
    }

    private int repeatInTable(Tbl table, Map<String, List<Map<String, String>>> tables) {
        int count = 0;
        List<Object> content = table.getContent();
        for (int i = 0; i < content.size(); i++) {
            Object rowObject = content.get(i);
            Object rowValue = unwrap(rowObject);
            if (!(rowValue instanceof Tr)) {
                continue;
            }

            RowTemplate rowTemplate = inspectRow((Tr) rowValue);
            if (rowTemplate == null || !tables.containsKey(rowTemplate.tableName)) {
                continue;
            }

            List<Object> repeatedRows = buildRepeatedRows(rowObject, rowTemplate, tables.get(rowTemplate.tableName));
            content.remove(i);
            content.addAll(i, repeatedRows);
            i += repeatedRows.size() - 1;
            count++;
        }
        return count;
    }

    private List<Object> buildRepeatedRows(Object templateRowObject, RowTemplate rowTemplate, List<Map<String, String>> rowData) {
        List<Object> repeatedRows = new ArrayList<>();
        for (Map<String, String> item : rowData) {
            Object rowCopy = XmlUtils.deepCopy(templateRowObject);
            placeholderReplacer.replaceInObject(rowCopy, valuesForRow(rowTemplate.tableName, rowTemplate.fields, item));
            repeatedRows.add(rowCopy);
        }
        return repeatedRows;
    }

    private Map<String, String> valuesForRow(String tableName, List<String> fields, Map<String, String> item) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : fields) {
            values.put(tableName + "[]." + field, item.getOrDefault(field, ""));
        }
        return values;
    }

    private RowTemplate inspectRow(Tr row) {
        Matcher matcher = TABLE_PLACEHOLDER.matcher(textOf(row));
        String tableName = null;
        List<String> fields = new ArrayList<>();
        while (matcher.find()) {
            String placeholderTableName = matcher.group(1);
            if (tableName == null) {
                tableName = placeholderTableName;
            } else if (!tableName.equals(placeholderTableName)) {
                return null;
            }
            fields.add(matcher.group(2));
        }
        if (tableName == null) {
            return null;
        }
        return new RowTemplate(tableName, fields);
    }

    private String textOf(Tr row) {
        StringBuilder text = new StringBuilder();
        new TraversalUtil(row, new TraversalUtil.CallbackImpl() {
            @Override
            public List<Object> apply(Object object) {
                Object value = unwrap(object);
                if (value instanceof Text) {
                    text.append(((Text) value).getValue());
                }
                return null;
            }
        });
        return text.toString();
    }

    private Object unwrap(Object object) {
        return object instanceof JAXBElement ? ((JAXBElement<?>) object).getValue() : object;
    }

    private static final class RowTemplate {
        private final String tableName;
        private final List<String> fields;

        private RowTemplate(String tableName, List<String> fields) {
            this.tableName = tableName;
            this.fields = fields;
        }
    }

    private final class TableCollector extends TraversalUtil.CallbackImpl {
        private final List<Tbl> tables = new ArrayList<>();

        @Override
        public List<Object> apply(Object object) {
            Object value = unwrap(object);
            if (value instanceof Tbl) {
                tables.add((Tbl) value);
            }
            return null;
        }
    }
}
