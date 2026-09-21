package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.input.ParserInputSource;
import com.dp.deviceops.parser.semantic.internal.SemanticEngine;
import com.dp.deviceops.parser.semantic.internal.SemanticLimits;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class DefaultDynamicSemanticParser implements DynamicSemanticParser {

    private final SemanticEngine engine;

    public DefaultDynamicSemanticParser() {
        this(SemanticLimits.defaults());
    }

    public DefaultDynamicSemanticParser(SemanticLimits limits) {
        this.engine = new SemanticEngine(Objects.requireNonNull(limits, "limits"));
    }

    @Override
    public SemanticParseResult parse(ParserPlan plan, ParserInputSource inputSource) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(inputSource, "inputSource");
        try (InputStream input = inputSource.openStream()) {
            EvidenceDocument document = plan.inputAdapter().decode(input);
            return engine.execute(document, plan);
        } catch (SemanticParserError exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SemanticParserError(SemanticParserError.INPUT_UNAVAILABLE,
                    "parser input cannot be read", exception);
        }
    }
}
