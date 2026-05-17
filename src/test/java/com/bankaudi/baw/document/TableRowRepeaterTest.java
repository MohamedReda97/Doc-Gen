package com.bankaudi.baw.document;

import com.bankaudi.baw.document.engine.TableRowRepeater;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableRowRepeaterTest {
    private final ObjectFactory factory = new ObjectFactory();

    @Test
    void repeatsTableRowForArrayItems() throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        Tbl table = factory.createTbl();
        table.getContent().add(row("CIF", "Customer name"));
        table.getContent().add(row("{{customers[].cif_number}}", "{{customers[].customer_full_name}}"));
        wordPackage.getMainDocumentPart().addObject(table);

        Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
        tables.put("customers", Arrays.asList(
                item("cif_number", "CIF-1", "customer_full_name", "First Customer"),
                item("cif_number", "CIF-2", "customer_full_name", "Second Customer")
        ));

        int repeatedRows = new TableRowRepeater().repeat(wordPackage, tables);

        assertEquals(1, repeatedRows);
        assertEquals(3, table.getContent().size());
        assertEquals("CIFCustomer name", rowText((Tr) table.getContent().get(0)));
        assertEquals("CIF-1First Customer", rowText((Tr) table.getContent().get(1)));
        assertEquals("CIF-2Second Customer", rowText((Tr) table.getContent().get(2)));
    }

    private Tr row(String... values) {
        Tr row = factory.createTr();
        for (String value : values) {
            Tc cell = factory.createTc();
            P paragraph = factory.createP();
            org.docx4j.wml.R run = factory.createR();
            Text text = factory.createText();
            text.setValue(value);
            run.getContent().add(text);
            paragraph.getContent().add(run);
            cell.getContent().add(paragraph);
            row.getContent().add(cell);
        }
        return row;
    }

    private Map<String, String> item(String key1, String value1, String key2, String value2) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put(key1, value1);
        item.put(key2, value2);
        return item;
    }

    private String rowText(Tr row) {
        StringBuilder text = new StringBuilder();
        new org.docx4j.TraversalUtil(row, new org.docx4j.TraversalUtil.CallbackImpl() {
            @Override
            public List<Object> apply(Object object) {
                if (object instanceof Text) {
                    text.append(((Text) object).getValue());
                }
                return null;
            }
        });
        return text.toString();
    }
}
