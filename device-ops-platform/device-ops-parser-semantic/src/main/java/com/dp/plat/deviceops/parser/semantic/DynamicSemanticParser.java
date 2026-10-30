package com.dp.plat.deviceops.parser.semantic;

import com.dp.plat.deviceops.parser.semantic.input.ParserInputSource;
import com.dp.plat.deviceops.parser.semantic.plan.ParserPlan;

public interface DynamicSemanticParser {
    SemanticParseResult parse(ParserPlan plan, ParserInputSource inputSource);
}
