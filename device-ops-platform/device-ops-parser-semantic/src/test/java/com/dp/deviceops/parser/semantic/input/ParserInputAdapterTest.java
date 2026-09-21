package com.dp.deviceops.parser.semantic.input;

import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.evidence.EvidenceUnit;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserInputAdapterTest {

    @Test
    void decodesExistingCommandOutputContractInCommandIndexOrder() throws Exception {
        String json = """
                {"schemaVersion":"1.0.0","collectionId":"fixture","commandBlocks":[
                  {"commandIndex":2,"commandText":"show second","status":"SUCCEEDED","stdout":"second"},
                  {"commandIndex":1,"commandText":"show version","status":"SUCCEEDED","stdout":"first"}
                ]}
                """;

        EvidenceDocument document = new CommandOutputBlockJsonInputAdapter().decode(input(json));

        assertEquals(List.of(1, 2), document.units().stream().map(EvidenceUnit::unitIndex).toList());
        assertEquals("show version", document.units().getFirst().attributes().get("commandText"));
        assertEquals("first", document.units().getFirst().contentLines().getFirst());
    }

    @Test
    void decodesLineLogsWithoutVendorOrCommandAssumptions() throws Exception {
        EvidenceDocument document = new LineLogInputAdapter().decode(input("first\r\nsecond\n"));

        assertEquals(List.of("first", "second"), document.units().stream()
                .map(unit -> unit.contentLines().getFirst()).toList());
        assertEquals(List.of("1", "2"), document.units().stream()
                .map(unit -> unit.attributes().get("lineNumber")).toList());
    }

    @Test
    void decodesExplicitSectionsAndRejectsInvalidSchema() throws Exception {
        String json = """
                {"schemaVersion":"1.0","sections":[
                  {"index":1,"type":"SECTION","attributes":{"name":"system"},"lines":["key: value"]}
                ]}
                """;

        EvidenceDocument document = new SectionTextInputAdapter().decode(input(json));

        assertEquals("system", document.units().getFirst().attributes().get("name"));
        assertEquals(List.of("key: value"), document.units().getFirst().contentLines());
        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> new SectionTextInputAdapter().decode(input("{\"schemaVersion\":\"2.0\",\"sections\":[]}")));
        assertEquals(SemanticParserError.INVALID_INPUT, error.code());
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
