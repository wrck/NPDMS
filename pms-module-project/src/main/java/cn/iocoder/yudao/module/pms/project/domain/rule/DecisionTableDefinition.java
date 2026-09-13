package cn.iocoder.yudao.module.pms.project.domain.rule;

import java.util.Map;

/** A named DMN table and its explicit bindings to the owner's field directory. */
public record DecisionTableDefinition(String key, String name, String decisionKey, String xml,
                                      Map<String, String> inputFields) {
    public DecisionTableDefinition {
        inputFields = inputFields == null ? Map.of() : Map.copyOf(inputFields);
    }
}
