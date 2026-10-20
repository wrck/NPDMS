package com.dp.plat.deviceops.parser.semantic;

import com.dp.plat.deviceops.core.model.CommandOutputBlock;

import java.util.List;

/** Framework-free, thread-safe boundary for target-level semantic parsing. */
public interface SemanticParser {

    SemanticParseResult parse(
            List<CommandOutputBlock> commandBlocks,
            SemanticParserSpecification specification);
}
