package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContract;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractJson;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractValidator;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractValidator.Checkpoint;
import static cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractValidator.RuleKind;

/** Bridges the optional operation contract to the existing version-local compiler and real Owner catalog. */
@Component
@RequiredArgsConstructor
public class TemplateOperationCompilation {
    private final ProjectBusinessOperationRegistry registry;
    private final ProjectRuleCompiler compiler;

    public record NodeBinding(String kind, String nodeKey, String path, TemplateDesignerDocument.WorkBindingSpec binding) { }
    public record FrozenBinding(String kind, String nodeKey, JsonNode contract) { }
    public record Result(List<FrozenBinding> bindings, List<Issue> issues) {
        public Result { bindings = List.copyOf(bindings); issues = List.copyOf(issues); }
        public static Result empty() { return new Result(List.of(), List.of()); }
        public void install(TemplateExecutionSnapshot snapshot) {
            for (var frozen : bindings) {
                var targets = "TASK".equals(frozen.kind())
                        ? snapshot.getTasks().stream().filter(node -> frozen.nodeKey().equals(node.getNodeKey()))
                            .map(TemplateExecutionSnapshot.TaskContract::getBinding).toList()
                        : snapshot.getStages().stream().filter(node -> frozen.nodeKey().equals(node.getNodeKey()))
                            .map(TemplateExecutionSnapshot.StageContract::getBinding).toList();
                if (targets.size() != 1 || targets.getFirst() == null)
                    throw new IllegalStateException("OPERATION_SNAPSHOT_TARGET_INVALID");
                targets.getFirst().setOperationContract(frozen.contract().deepCopy());
            }
        }
    }

    public static boolean hasContracts(TemplateDesignerDocument source) { return !bindings(source).isEmpty(); }

    public static List<NodeBinding> bindings(TemplateDesignerDocument source) {
        List<NodeBinding> result = new ArrayList<>();
        if (source == null) return result;
        if (source.getStages() != null) for (int i = 0; i < source.getStages().size(); i++) {
            var node = source.getStages().get(i);
            if (node != null && node.getWorkBinding() != null && node.getWorkBinding().getOperationContract() != null)
                result.add(new NodeBinding("STAGE", node.getNodeKey(), "stages[" + i + "].workBinding.operationContract", node.getWorkBinding()));
        }
        if (source.getTasks() != null) for (int i = 0; i < source.getTasks().size(); i++) {
            var node = source.getTasks().get(i);
            if (node != null && node.getWorkBinding() != null && node.getWorkBinding().getOperationContract() != null)
                result.add(new NodeBinding("TASK", node.getNodeKey(), "tasks[" + i + "].workBinding.operationContract", node.getWorkBinding()));
        }
        return result;
    }

    public Result compile(TemplateDesignerDocument source) {
        var bindings = bindings(source);
        if (bindings.isEmpty()) return Result.empty();
        List<Issue> issues = new ArrayList<>();
        var definitions = TemplateRuleCollection.index(source.getRules());
        Map<String, RuleProgram> programs = new LinkedHashMap<>();
        Map<String, TemplateOperationContractValidator.RuleMetadata> metadata = new LinkedHashMap<>();
        for (var entry : definitions.entrySet()) {
            var rule = entry.getValue();
            if (rule.kind() != VersionRule.Kind.CONDITION) {
                metadata.put(entry.getKey(), new TemplateOperationContractValidator.RuleMetadata(RuleKind.DECISION, Set.of()));
                continue;
            }
            try {
                var program = compiler.compile(TemplateRuleCollection.condition(definitions, entry.getKey()));
                programs.put(entry.getKey(), program);
                Set<Checkpoint> available = java.util.EnumSet.noneOf(Checkpoint.class);
                if (supports(program, Checkpoint.PRE)) available.add(Checkpoint.PRE);
                if (supports(program, Checkpoint.POST)) available.add(Checkpoint.POST);
                metadata.put(entry.getKey(), new TemplateOperationContractValidator.RuleMetadata(RuleKind.CONDITION, available));
            } catch (RuntimeException invalid) {
                // The main compiler also reports grammar errors. The referenced rule remains unavailable here.
                metadata.put(entry.getKey(), new TemplateOperationContractValidator.RuleMetadata(RuleKind.CONDITION, Set.of()));
            }
        }
        var operations = registry.all().stream().map(descriptor ->
                new TemplateOperationContractValidator.OperationMetadata(descriptor.operationCode(), descriptor.operationVersion(),
                        descriptor.ownerContext(), descriptor.objectType(), descriptor.checkpoints().stream()
                        .map(Checkpoint::valueOf).collect(java.util.stream.Collectors.toSet()))).toList();
        List<FrozenBinding> frozen = new ArrayList<>();
        for (var entry : bindings) {
            try {
                var binding = entry.binding();
                var input = TemplateOperationContractJson.readAuthoring(binding.getOperationContract());
                var checked = TemplateOperationContractValidator.validate(input,
                        new TemplateOperationContractValidator.Binding(binding.getType(), binding.getTargetContextCode(), binding.getTargetObjectType()),
                        operations, metadata, entry.path());
                issues.addAll(checked.issues().stream().map(issue -> new Issue(issue.path(), issue.code(), issue.message())).toList());
                if (!checked.valid()) continue;
                ObjectNode value = (ObjectNode) TemplateOperationContractJson.authoring(checked.contract());
                Map<String, RuleProgram> pinned = new TreeMap<>();
                for (String key : checked.referencedRuleKeys()) {
                    var program = programs.get(key);
                    if (program == null) throw new IllegalArgumentException("OPERATION_RULE_NOT_COMPILED");
                    // DECISION references were expanded by TemplateRuleCollection.condition. Their exact DMN is included.
                    pinned.put(key, program);
                }
                value.set("programs", JsonUtils.parseTree(JsonUtils.toJsonString(pinned)));
                frozen.add(new FrozenBinding(entry.kind(), entry.nodeKey(), value));
            } catch (RuntimeException invalid) {
                issues.add(new Issue(entry.path(), "OPERATION_CONTRACT_INVALID", "操作子契约格式或引用无效，请检查具体操作和前后置规则"));
            }
        }
        return new Result(frozen, issues);
    }

    /** Supported synchronous facts are deliberately narrower than node-completion facts. */
    public static boolean supports(RuleProgram program, Checkpoint checkpoint) {
        if (program == null || program.kind() != VersionRule.Kind.CONDITION || program.leaves().isEmpty()) return false;
        for (var leaf : program.leaves()) {
            if ("CONSTANT".equals(leaf.predicate())) continue;
            if ("FIELD".equals(leaf.predicate())
                    && ProjectRuleFields.codes().contains(leaf.parameters().path("fieldCode").asText())) continue;
            if ("DECISION".equals(leaf.predicate())) {
                var table = leaf.parameters().path("table");
                var inputs = table.path("inputFields");
                if (!table.isObject() || !inputs.isObject()) return false;
                boolean fieldsKnown = inputs.properties().stream().allMatch(item -> item.getValue().isTextual()
                        && ProjectRuleFields.codes().contains(item.getValue().asText()));
                if (fieldsKnown) continue;
                return false;
            }
            if (checkpoint == Checkpoint.PRE && "TIME_REACHED".equals(leaf.predicate())) continue;
            // A task/approval/business result or future callback is not an atomic operation postcondition.
            // New fact types require an actual resolver and metadata before they can be selected here.
            return false;
        }
        return true;
    }
}
