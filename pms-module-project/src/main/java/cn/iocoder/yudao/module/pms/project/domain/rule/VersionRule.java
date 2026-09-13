package cn.iocoder.yudao.module.pms.project.domain.rule;

import tools.jackson.databind.JsonNode;

/** Local to one template/project-plan version. Sharing is an explicit authoring decision. */
public record VersionRule(String key, String name, Kind kind, boolean shared,
                          JsonNode expression, DecisionTableDefinition decision) {
    public enum Kind { CONDITION, DECISION }

    public VersionRule {
        expression = expression == null ? null : expression.deepCopy();
    }
}
