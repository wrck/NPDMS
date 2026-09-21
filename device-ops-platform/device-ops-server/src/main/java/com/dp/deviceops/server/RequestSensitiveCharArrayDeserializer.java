package com.dp.deviceops.server;

import com.dp.deviceops.adapter.web.RequestSensitiveCharArrayRegistry;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Arrays;

/** Registers every request-decoded char array before Jackson can encounter a later body error. */
final class RequestSensitiveCharArrayDeserializer extends StdDeserializer<char[]> {
    RequestSensitiveCharArrayDeserializer() {
        super(char[].class);
    }

    @Override
    public char[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (char[]) context.handleUnexpectedToken(char[].class, parser);
        }
        char[] source = parser.getTextCharacters();
        char[] sensitive = Arrays.copyOfRange(source, parser.getTextOffset(),
                parser.getTextOffset() + parser.getTextLength());
        RequestSensitiveCharArrayRegistry.register(sensitive);
        return sensitive;
    }
}
