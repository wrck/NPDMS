package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalJsonTest {

    @Test
    void sortsObjectKeysAndUsesOneTrailingLf() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("z", 1);
        value.put("a", Map.of("y", 2, "b", 3));

        assertEquals("{\"a\":{\"b\":3,\"y\":2},\"z\":1}\n", new CanonicalJson().text(value));
    }

    @Test
    void semanticResultIsDeeplyImmutable() {
        SemanticParseResult result = new SemanticParseResult(
                "1.0.0", "1.0.0", "1.0.0", "1.0.0",
                "a".repeat(64), "b".repeat(64), "c".repeat(64),
                Map.of("nested", Map.of("value", 1)), Map.of(), Map.of(), List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.snapshot().get("nested");
        assertThrows(UnsupportedOperationException.class, () -> nested.put("other", 2));
    }
}
