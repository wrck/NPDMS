package com.dp.deviceops.parser.semantic.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.dp.deviceops.parser.semantic.SemanticParserError;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CanonicalJson {

    private final ObjectMapper mapper;

    public CanonicalJson() {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    public byte[] bytes(Object value) {
        try {
            byte[] json = mapper.writeValueAsBytes(value);
            byte[] terminated = new byte[json.length + 1];
            System.arraycopy(json, 0, terminated, 0, json.length);
            terminated[json.length] = '\n';
            return terminated;
        } catch (JsonProcessingException exception) {
            throw new SemanticParserError(SemanticParserError.INTERNAL_ERROR,
                    "result serialization failed", exception);
        }
    }

    public String text(Object value) {
        return new String(bytes(value), StandardCharsets.UTF_8);
    }

    public String sha256(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(value)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public ObjectMapper mapper() {
        return mapper.copy();
    }
}
