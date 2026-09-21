package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.input.ParserInputSource;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;

public interface DynamicSemanticParser {
    SemanticParseResult parse(ParserPlan plan, ParserInputSource inputSource);
}
