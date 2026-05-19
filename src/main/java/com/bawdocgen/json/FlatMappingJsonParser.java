package com.bawdocgen.json;

import com.bawdocgen.api.DocumentGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class FlatMappingJsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    public Map<String, String> parse(String json) throws DocumentGenerationException {
        if (json == null || json.trim().isEmpty()) {
            throw new DocumentGenerationException("DOC-004", "JSON payload is empty");
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode mappingNode = root.has("flat_mapping") ? root.get("flat_mapping") : root;
            if (mappingNode == null || !mappingNode.isObject()) {
                throw new DocumentGenerationException("DOC-004", "JSON must contain an object or a flat_mapping object");
            }

            Map<String, Object> raw = MAPPER.convertValue(mappingNode, MAP_TYPE);
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                result.put(entry.getKey(), stringify(entry.getValue()));
            }
            return result;
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new DocumentGenerationException("DOC-004", "Failed to parse JSON flat_mapping", e);
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }
}
