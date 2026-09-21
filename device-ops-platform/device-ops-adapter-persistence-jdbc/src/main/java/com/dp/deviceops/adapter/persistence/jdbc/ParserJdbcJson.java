package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.semantic.GenericContent;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ParserJdbcJson {

    private final ObjectMapper mapper;

    ParserJdbcJson(ObjectMapper mapper) {
        SimpleModule genericSections = new SimpleModule("parser-generic-sections");
        genericSections.addDeserializer(GenericContent.Section.class, new GenericSectionDeserializer());
        this.mapper = mapper.copy().registerModule(new JavaTimeModule()).registerModule(genericSections);
    }

    String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("parser persistence JSON cannot be encoded", exception);
        }
    }

    <T> T decode(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("parser persistence JSON cannot be decoded", exception);
        }
    }

    <T> T decode(String value, TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("parser persistence JSON cannot be decoded", exception);
        }
    }

    ObjectMapper mapper() {
        return mapper.copy();
    }

    /** Canonical sections flatten their payload via JsonAnyGetter; reconstruct that map on reads. */
    private static final class GenericSectionDeserializer extends JsonDeserializer<GenericContent.Section> {
        private static final Set<String> COMMON = Set.of(
                "sectionIndex", "type", "startLine", "endLine", "rawLines", "warnings");
        private static final TypeReference<List<String>> LINES = new TypeReference<>() { };
        private static final TypeReference<List<GenericContent.Warning>> WARNINGS = new TypeReference<>() { };

        @Override
        public GenericContent.Section deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode node = mapper.readTree(parser);
            Map<String, Object> fields = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                if (!COMMON.contains(entry.getKey())) {
                    fields.put(entry.getKey(), mapper.convertValue(entry.getValue(), Object.class));
                }
            });
            return new GenericContent.Section(node.required("sectionIndex").intValue(),
                    node.required("type").textValue(), node.required("startLine").intValue(),
                    node.required("endLine").intValue(), mapper.convertValue(node.required("rawLines"), LINES),
                    fields, mapper.convertValue(node.required("warnings"), WARNINGS));
        }
    }

    static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return ((Timestamp) value).toInstant();
    }
}
