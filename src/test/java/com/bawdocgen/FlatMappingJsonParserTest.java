package com.bawdocgen;

import com.bawdocgen.json.FlatMappingJsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlatMappingJsonParserTest {
    @Test
    void parsesFlatMappingWrapper() throws Exception {
        String json = "{\"flat_mapping\":{\"personal.first_name\":\"Karim\",\"loan.amount_requested\":25000,\"blank\":null}}";

        Map<String, String> values = new FlatMappingJsonParser().parse(json);

        assertEquals("Karim", values.get("personal.first_name"));
        assertEquals("25000", values.get("loan.amount_requested"));
        assertEquals("", values.get("blank"));
    }

    @Test
    void parsesRawObjectForSimpleBawCalls() throws Exception {
        String json = "{\"personal.first_name\":\"Karim\"}";

        Map<String, String> values = new FlatMappingJsonParser().parse(json);

        assertEquals("Karim", values.get("personal.first_name"));
    }
}
