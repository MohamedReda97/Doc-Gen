package com.bankaudi.baw.document;

import com.bankaudi.baw.document.json.TableMappingJsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableMappingJsonParserTest {
    @Test
    void parsesTablesObjectAsStringMaps() throws Exception {
        String json = "{"
                + "\"flat_mapping\":{\"DATE\":\"17/05/2026\"},"
                + "\"tables\":{\"customers\":[{\"cif_number\":\"CIF-1\",\"customer_full_name\":\"First\"},"
                + "{\"cif_number\":2,\"customer_full_name\":null}]}"
                + "}";

        Map<String, List<Map<String, String>>> tables = new TableMappingJsonParser().parse(json);

        assertEquals(1, tables.size());
        assertEquals("CIF-1", tables.get("customers").get(0).get("cif_number"));
        assertEquals("2", tables.get("customers").get(1).get("cif_number"));
        assertEquals("", tables.get("customers").get(1).get("customer_full_name"));
    }
}
