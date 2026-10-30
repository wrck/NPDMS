package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class TemplateVersionedCompilationTest {
    @Test
    void versionedCompilerFreezesEverythingWithoutInvokingTheLegacyHasher() {
        var designer = TemplateVersionSnapshotTest.designer();
        String before = JsonUtils.toJsonString(designer);
        TemplateCompiler.Compilation result;
        try (var hasher = mockStatic(TemplateExecutionSnapshotHasher.class)) {
            result = new TemplateCompiler().compileVersioned(designer);
            assertTrue(result.valid(), () -> result.issues().toString());
            assertNull(result.snapshotHash());
            hasher.verifyNoInteractions();
        }
        assertEquals(3, result.snapshot().getExecutionSchemaVersion());
        assertEquals(TemplateCompiler.VERSIONED_COMPILER_VERSION, result.snapshot().getCompilerVersion());
        assertEquals(result.snapshot(), TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(result.snapshot())));
        assertEquals(before, JsonUtils.toJsonString(designer));
        designer.getStages().getFirst().setName("后来编辑的草稿");
        assertNotEquals(designer.getStages().getFirst().getName(), result.snapshot().getStages().getFirst().getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MATCH", "CLOSURE", "ADMISSION", "EXIT", "DECISION"})
    void changedRulesProduceDifferentFrozenContentAndDoNotMutateThePreviousVersion(String change) {
        var designer = TemplateVersionSnapshotTest.designer();
        var compiler = new TemplateCompiler();
        var first = compiler.compileVersioned(designer);
        assertTrue(first.valid());
        String frozen = JsonUtils.toJsonString(first.snapshot());
        if (!change.equals("DECISION")) designer.getRules().add(new VersionRule("different", "另一个条件", VersionRule.Kind.CONDITION, true,
                JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}"), null));
        switch (change) {
            case "MATCH" -> designer.setMatchRuleKey("different");
            case "CLOSURE" -> designer.setClosureRuleKey("different");
            case "ADMISSION" -> designer.getStages().getFirst().setAdmissionRuleKey("different");
            case "EXIT" -> designer.getTasks().getFirst().setExitRuleKey("different");
            case "DECISION" -> {
                var decision = designer.getRules().stream().filter(r -> r.key().equals("decision")).findFirst().orElseThrow();
                var table = decision.decision();
                designer.getRules().set(designer.getRules().indexOf(decision), new VersionRule(decision.key(), decision.name(),
                        decision.kind(), decision.shared(), null,
                        new cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition(table.key(), table.name(),
                                table.decisionKey(), table.xml().replace(">1<", ">2<"), table.inputFields())));
            }
            default -> throw new AssertionError(change);
        }
        var second = compiler.compileVersioned(designer);
        assertTrue(second.valid(), () -> second.issues().toString());
        switch (change) {
            case "MATCH" -> assertEquals("different", second.snapshot().getMatchRuleKey());
            case "CLOSURE" -> assertEquals("different", second.snapshot().getClosureRuleKey());
            case "ADMISSION" -> assertEquals("different", second.snapshot().getStages().getFirst().getAdmissionRuleKey());
            case "EXIT" -> assertEquals("different", second.snapshot().getTasks().getFirst().getExitRuleKey());
            case "DECISION" -> assertNotEquals(first.snapshot().getRulePrograms().get("decision"),
                    second.snapshot().getRulePrograms().get("decision"));
            default -> throw new AssertionError(change);
        }
        assertNotEquals(first.snapshot(), second.snapshot());
        assertEquals(frozen, JsonUtils.toJsonString(first.snapshot()));
        assertEquals(first.snapshot(), TemplateExecutionSnapshotReader.read(frozen));
    }

    @Test
    void legacyCompilerAndHashRemainCompatible() {
        var designer = TemplateVersionSnapshotTest.designer();
        var compiler = new TemplateCompiler();
        var before = compiler.compile(designer);
        compiler.compileVersioned(designer);
        var after = compiler.compile(designer);
        assertTrue(before.valid());
        assertEquals(2, before.snapshot().getExecutionSchemaVersion());
        assertEquals(before, after);
        assertEquals(before.snapshotHash(), TemplateExecutionSnapshotHasher.hash(before.snapshot()));
    }

    @Test
    void incompleteNewSnapshotReturnsPublicationIssueRatherThanDefaulting() {
        var designer = TemplateVersionSnapshotTest.designer();
        designer.getMilestones().getFirst().setNodeKey(null);
        var result = new TemplateCompiler().compileVersioned(designer);
        assertFalse(result.valid());
        assertNull(result.snapshot());
        assertNull(result.snapshotHash());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("INCOMPLETE_VERSION_SNAPSHOT")));
    }
}
