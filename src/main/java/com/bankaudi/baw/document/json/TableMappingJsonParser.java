package com.bankaudi.baw.document.json;

import com.bankaudi.baw.document.api.DocumentGenerationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableMappingJsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, List<Map<String, String>>> parse(String json) throws DocumentGenerationException {
        if (json == null || json.trim().isEmpty()) {
            throw new DocumentGenerationException("DOC-004", "JSON payload is empty");
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode tablesNode = root.get("tables");
            Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
            if (tablesNode == null || tablesNode.isNull()) {
                return tables;
            }
            if (!tablesNode.isObject()) {
                throw new DocumentGenerationException("DOC-004", "JSON tables must be an object");
            }

            tablesNode.fields().forEachRemaining(entry -> tables.put(entry.getKey(), parseRows(entry.getValue())));
            return tables;
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new DocumentGenerationException("DOC-004", "Failed to parse JSON tables", e);
        }
    }

    private List<Map<String, String>> parseRows(JsonNode rowsNode) {
        if (rowsNode == null || !rowsNode.isArray()) {
            throw new IllegalArgumentException("Table data must be an array");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode rowNode : rowsNode) {
            if (!rowNode.isObject()) {
                throw new IllegalArgumentException("Table row data must be an object");
            }
            Map<String, String> row = new LinkedHashMap<>();
            rowNode.fields().forEachRemaining(entry -> row.put(entry.getKey(), stringify(entry.getValue())));
            rows.add(row);
        }
        return rows;
    }

    private String stringify(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText();
        }
        return value.asText();
    }
}
