package cn.iocoder.yudao.module.pms.project.domain.rule;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** Immutable compiler output. EL is generated, never a separately editable rule source. */
public record RuleProgram(VersionRule.Kind kind, String el, List<Leaf> leaves) {
    public RuleProgram {
        leaves = List.copyOf(leaves);
    }

    public record Leaf(String key, String path, String predicate, JsonNode parameters) { }
}
