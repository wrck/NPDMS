package com.dp.deviceops.parser.semantic.input;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.evidence.EvidenceUnit;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SectionTextInputAdapter implements ParserInputAdapter {

    private final ObjectMapper objectMapper;

    public SectionTextInputAdapter() {
        this(new CanonicalJson().mapper());
    }

    public SectionTextInputAdapter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public String inputFormat() {
        return "section-text/v1";
    }

    @Override
    public EvidenceDocument decode(InputStream input) throws IOException {
        try {
            SectionDocument document = objectMapper.readValue(
                    Objects.requireNonNull(input, "input"), SectionDocument.class);
            if (document == null || !"1.0".equals(document.schemaVersion())
                    || document.sections() == null) {
                throw invalid("section text schemaVersion or sections is invalid");
            }
            return new EvidenceDocument(document.sections().stream().map(section ->
                    new EvidenceUnit(section.index(),
                            section.type() == null ? "SECTION" : section.type(),
                            section.attributes() == null ? Map.of() : section.attributes(),
                            Objects.requireNonNull(section.lines(), "section lines"),
                            List.of(), false)).toList());
        } catch (JsonProcessingException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "section text JSON is invalid", exception);
        } catch (SemanticParserError exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "section fields are invalid", exception);
        }
    }

    private static SemanticParserError invalid(String message) {
        return new SemanticParserError(SemanticParserError.INVALID_INPUT, message);
    }

    private record SectionDocument(String schemaVersion, List<Section> sections) {
    }

    private record Section(
            int index,
            String type,
            Map<String, String> attributes,
            List<String> lines) {
    }
}
