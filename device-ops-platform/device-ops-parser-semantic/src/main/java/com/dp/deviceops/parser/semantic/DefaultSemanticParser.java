package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.input.CommandOutputBlockEvidenceAdapter;
import com.dp.deviceops.parser.semantic.input.CommandOutputBlockJsonInputAdapter;
import com.dp.deviceops.parser.semantic.input.LineLogInputAdapter;
import com.dp.deviceops.parser.semantic.input.SectionTextInputAdapter;
import com.dp.deviceops.parser.semantic.internal.SemanticEngine;
import com.dp.deviceops.parser.semantic.internal.SemanticLimits;
import com.dp.deviceops.parser.semantic.plan.ParserExtensionRegistry;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class DefaultSemanticParser implements SemanticParser {

    private static final String RULES_RESOURCE = "/semantic/show-tech-semantic-rules.json";
    private static final String PROJECTIONS_RESOURCE = "/semantic/projection-profiles.json";
    private static final String INPUT_FORMAT = "command-output-block/v1";

    private final CommandOutputBlockEvidenceAdapter commandAdapter;
    private final SemanticEngine engine;
    private final ParserPlanCompiler planCompiler;
    private final SemanticParserSpecification bundledSpecification;
    private final ParserPlan bundledPlan;

    public DefaultSemanticParser() {
        this(SemanticLimits.defaults());
    }

    public DefaultSemanticParser(SemanticLimits limits) {
        Objects.requireNonNull(limits, "limits");
        this.commandAdapter = new CommandOutputBlockEvidenceAdapter();
        this.engine = new SemanticEngine(limits);
        this.planCompiler = new ParserPlanCompiler(limits, List.of(
                new CommandOutputBlockJsonInputAdapter(),
                new LineLogInputAdapter(),
                new SectionTextInputAdapter()), new ParserExtensionRegistry(List.of()));
        this.bundledSpecification = bundledSpecification();
        this.bundledPlan = planCompiler.compileLegacy(
                "device.show-tech", "bundled", INPUT_FORMAT, bundledSpecification);
    }

    public static DefaultSemanticParser bundled() {
        return new DefaultSemanticParser();
    }

    public static SemanticParserSpecification bundledSpecification() {
        return new SemanticParserSpecification(readResource(RULES_RESOURCE), readResource(PROJECTIONS_RESOURCE));
    }

    @Override
    public SemanticParseResult parse(
            List<CommandOutputBlock> commandBlocks,
            SemanticParserSpecification specification) {
        return parseEvidence(commandAdapter.fromBlocks(commandBlocks), specification);
    }

    SemanticParseResult parseEvidence(
            EvidenceDocument document,
            SemanticParserSpecification specification) {
        Objects.requireNonNull(specification, "specification");
        ParserPlan plan = bundledSpecification.equals(specification)
                ? bundledPlan
                : planCompiler.compileLegacy("device.show-tech", "legacy", INPUT_FORMAT, specification);
        return engine.execute(document, plan);
    }

    private static String readResource(String path) {
        try (InputStream stream = DefaultSemanticParser.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("bundled semantic catalog is missing");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("bundled semantic catalog cannot be read", exception);
        }
    }
}
