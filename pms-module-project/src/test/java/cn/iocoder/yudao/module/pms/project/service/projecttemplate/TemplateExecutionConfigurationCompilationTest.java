package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplateExecutionConfigurationCompilationTest {
    static final String OP = "SOL.SITE_SURVEY.CONFIRM";
    public static class Owner { public void confirm() { throw new AssertionError("配置查询不能执行业务"); } }

    static TemplateDesignerDocument source() {
        var source = TemplateVersionSnapshotTest.designer();
        var task = source.getTasks().getFirst();
        var binding = task.getWorkBinding();
        binding.setType("BUSINESS_OBJECT"); binding.setTargetContextCode("SOL");
        binding.setTargetObjectType("SITE_SURVEY"); binding.setTargetObjectKey("SURVEY");
        task.getCompletionRule().setExpression(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));
        task.setExecution(JsonUtils.parseTree(TemplateExecutionConfigurationTest.OPERATION));
        return source;
    }

    static TemplateCompiler compiler(String mode) {
        var descriptor = new ProjectBusinessOperationDescriptor(OP, 1, "SOL", "SITE_SURVEY", "确认", "CONFIRM",
                Set.of("PRE", "POST"), Owner.class, "confirm");
        var second = new ProjectBusinessOperationDescriptor(mode.equals("VERSION") ? OP : "SOL.SITE_SURVEY.UPDATE",
                mode.equals("VERSION") ? 2 : 1, "SOL", "SITE_SURVEY", "修改", "UPDATE", Set.of("PRE", "POST"), Owner.class, "confirm");
        ProjectBusinessOperationProvider provider = new ProjectBusinessOperationProvider() {
            public List<ProjectBusinessOperationDescriptor> operations() {
                return mode.equals("AMBIGUOUS") || mode.equals("VERSION") ? List.of(descriptor, second) : List.of(descriptor);
            }
            public Map<String, String> permissionCodes() {
                return operations().stream().collect(java.util.stream.Collectors.toMap(ProjectBusinessOperationDescriptor::operationCode,
                        row -> "pms:eng-site-survey:update", (a, b) -> a));
            }
        };
        var registry = new ProjectBusinessOperationRegistry(List.of(provider), mode.equals("NO_RUNTIME") ? List.of() : List.of((code, version) -> true));
        var compiler = new TemplateCompiler();
        ReflectionTestUtils.setField(compiler, "executionConfigurations", new TemplateExecutionConfigurationCompilation(registry));
        ReflectionTestUtils.setField(compiler, "operationCompilation", new TemplateOperationCompilation(registry, new ProjectRuleCompiler()));
        return compiler;
    }

    @Test
    void permissionShorthandFreezesExactOperationAndRulesWithoutModifyingTheDraft() {
        var source = source();
        String before = JsonUtils.toJsonString(source);
        var compiled = compiler("OK").compileVersioned(source);
        assertTrue(compiled.valid(), () -> compiled.issues().toString());
        assertNull(compiled.snapshotHash());
        var task = compiled.snapshot().getTasks().getFirst();
        assertEquals(OP, task.getExecution().path("operations").get(0).path("operationCode").textValue());
        assertFalse(task.getExecution().path("operations").get(0).has("operationVersion"));
        assertEquals(1, task.getBinding().getOperationContract().path("operations").get(0).path("operationVersion").intValue());
        assertEquals(before, JsonUtils.toJsonString(source));
        assertEquals(compiled.snapshot(), TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(compiled.snapshot())));
        assertTrue(task.getBinding().getOperationContract().path("programs").has("ready"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AMBIGUOUS", "VERSION", "NO_RUNTIME", "OWNER", "UNKNOWN_PERMISSION", "DUPLICATE", "SUBSCRIPTION", "PRESENTATION"})
    void unavailableOrAmbiguousCapabilitiesNeverProducePublishedSnapshots(String mode) {
        var source = source();
        ObjectNode config = (ObjectNode) source.getTasks().getFirst().getExecution();
        ObjectNode operation = (ObjectNode) config.path("operations").get(0);
        switch (mode) {
            case "OWNER" -> operation.put("ownerContext", "ACC");
            case "UNKNOWN_PERMISSION" -> operation.put("permissionCode", "not-registered");
            case "DUPLICATE" -> ((tools.jackson.databind.node.ArrayNode) config.get("operations")).add(operation.deepCopy());
            case "SUBSCRIPTION" -> config.set("subscriptions", JsonUtils.parseTree(TemplateExecutionConfigurationTest.SUBSCRIPTION).get("subscriptions"));
            case "PRESENTATION" -> config.set("presentation", JsonUtils.parseTree("{\"pageUrl\":\"/pms/delivery-business/site-survey\"}"));
        }
        var compiled = compiler(mode).compileVersioned(source);
        assertFalse(compiled.valid()); assertNull(compiled.snapshot()); assertNull(compiled.snapshotHash());
        assertFalse(compiled.issues().isEmpty());
    }

    @Test
    void explicitActionDisambiguatesSharedPermissionWithoutChoosingLatestVersion() {
        var source = source();
        ((ObjectNode) source.getTasks().getFirst().getExecution().path("operations").get(0)).put("operationCode", OP);
        assertTrue(compiler("AMBIGUOUS").compileVersioned(source).valid());
        assertFalse(compiler("VERSION").compileVersioned(source).valid());
    }

    @Test
    void legacyFormatCannotCarryNewExecutionAndTheFrozenActionCannotDiverge() {
        var source = source();
        assertFalse(compiler("OK").compile(source).valid());
        var snapshot = compiler("OK").compileVersioned(source).snapshot();
        var task = snapshot.getTasks().getFirst();
        ((ObjectNode) task.getExecution().path("operations").get(0)).put("operationCode", "SOL.SITE_SURVEY.DELETE");
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(snapshot)));
        snapshot.setExecutionSchemaVersion(2);
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionSnapshotReader.validate(snapshot));
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(snapshot)));
    }

    @Test
    void displayOrderCanDifferFromTheCanonicalRuntimeOperationOrder() {
        var source = source();
        var config = (ObjectNode) source.getTasks().getFirst().getExecution();
        var rows = (tools.jackson.databind.node.ArrayNode) config.get("operations");
        var first = (ObjectNode) rows.get(0);
        first.put("operationCode", "SOL.SITE_SURVEY.UPDATE");
        var second = first.deepCopy(); second.put("operationCode", OP); rows.add(second);
        var compiled = compiler("AMBIGUOUS").compileVersioned(source);
        assertTrue(compiled.valid(), () -> compiled.issues().toString());
        var task = compiled.snapshot().getTasks().getFirst();
        assertEquals("SOL.SITE_SURVEY.UPDATE", task.getExecution().path("operations").get(0).path("operationCode").asText());
        assertEquals(OP, task.getBinding().getOperationContract().path("operations").get(0).path("operationCode").asText());
        assertEquals(compiled.snapshot(), TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(compiled.snapshot())));
    }

    @Test
    void twoEditableOperationSourcesAreRejected() {
        var source = source();
        source.getTasks().getFirst().getWorkBinding().setOperationContract(JsonUtils.parseTree(
                "{\"version\":1,\"operations\":[{\"operationCode\":\"" + OP + "\",\"operationVersion\":1,\"pre\":{\"mode\":\"NONE\"},\"post\":{\"mode\":\"NONE\"}}]}"));
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.forEditing(source));
    }
}
