package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 格式3只校验版本内冻结结构，不求值规则、不编译EL、不查询当前业务或配置。 */
public final class TemplateVersionSnapshot {
    public static final int SCHEMA_VERSION = 3;

    private TemplateVersionSnapshot() { }

    static void requireDocument(JsonNode document) {
        for (String name : List.of("rules", "stages", "tasks", "milestones", "deliverables", "gates", "transitions")) {
            require(document.path(name).isArray(), "缺少冻结集合: " + name);
        }
        require(document.path("rulePrograms").isObject(), "缺少冻结规则程序");
        require(document.path("match").isObject(), "缺少冻结匹配配置");
    }

    public static void validate(TemplateExecutionSnapshot snapshot) {
        require(snapshot != null && Integer.valueOf(SCHEMA_VERSION).equals(snapshot.getExecutionSchemaVersion()), "格式不受支持");
        require(text(snapshot.getCompilerVersion()), "缺少编译器标识");
        require(snapshot.getMatch() != null && snapshot.getRules() != null && snapshot.getRulePrograms() != null
                && snapshot.getStages() != null && !snapshot.getStages().isEmpty() && snapshot.getTasks() != null
                && snapshot.getMilestones() != null && snapshot.getDeliverables() != null
                && snapshot.getGates() != null && snapshot.getTransitions() != null, "冻结内容不完整");

        Map<String, VersionRule> rules = TemplateRuleCollection.index(snapshot.getRules());
        Map<String, RuleProgram> programs = snapshot.getRulePrograms();
        Set<String> expectedPrograms = new HashSet<>(rules.keySet());
        for (VersionRule rule : rules.values()) {
            RuleProgram program = requireProgram(programs, rule.key(), rule.kind());
            if (rule.kind() == VersionRule.Kind.CONDITION) {
                JsonNode expression = TemplateRuleCollection.condition(rules, rule.key());
                DeliveryDefinitionPayloadValidator.rule(expression);
                verifyLeaves(expression, "rule", program.leaves(), new int[]{0}, true);
            } else {
                require(program.leaves().size() == 1 && "DECISION_VALUE".equals(program.leaves().getFirst().predicate()), "策略程序不完整");
                JsonNode expected = JsonUtils.parseTree(JsonUtils.toJsonString(rule.decision()));
                require(expected.equals(program.leaves().getFirst().parameters().path("table")), "策略表与冻结程序不一致");
            }
        }
        condition(programs, snapshot.getMatchRuleKey());
        condition(programs, snapshot.getClosureRuleKey());

        Set<String> nodeKeys = new HashSet<>();
        Map<String, TemplateExecutionSnapshot.StageContract> stages = new LinkedHashMap<>();
        for (var stage : snapshot.getStages()) {
            require(stage != null, "阶段为空");
            node(stage.getNodeKey(), stage.getCode(), nodeKeys, stages, stage);
            require(text(stage.getName()) && stage.getLifecycleStage() != null && stage.getLifecycleStage().matches("S[0-6]"), "阶段基础信息缺失");
            nodeRules(programs, rules, stage.getAdmissionRuleKey(), stage.getCompletionRuleKey(), stage.getExitRuleKey(), stage.getCompletionRule());
            binding(stage.getBinding(), programs);
            execution(stage.getExecution(), stage.getBinding());
            require(stage.getBinding() == null || stage.getPermission() != null, "阶段绑定缺少权限契约");
        }
        Map<String, TemplateExecutionSnapshot.TaskContract> tasks = new LinkedHashMap<>();
        for (var task : snapshot.getTasks()) {
            require(task != null, "任务为空");
            node(task.getNodeKey(), task.getCode(), nodeKeys, tasks, task);
            require(text(task.getName()) && stages.containsKey(task.getStageCode()), "任务所属阶段缺失");
            require(task.getBinding() != null && task.getPermission() != null, "任务执行契约缺失");
            nodeRules(programs, rules, task.getAdmissionRuleKey(), task.getCompletionRuleKey(), task.getExitRuleKey(), task.getCompletionRule());
            binding(task.getBinding(), programs);
            execution(task.getExecution(), task.getBinding());
        }
        for (var task : tasks.values()) {
            Set<String> path = new HashSet<>();
            var current = task;
            while (current != null) {
                require(path.add(current.getCode()), "任务父子循环");
                String parent = current.getParentTaskCode();
                require(!text(parent) || tasks.containsKey(parent), "父任务缺失");
                current = text(parent) ? tasks.get(parent) : null;
            }
        }
        Map<String, TemplateExecutionSnapshot.MilestoneContract> milestones = new LinkedHashMap<>();
        for (var milestone : snapshot.getMilestones()) {
            require(milestone != null, "里程碑为空");
            node(milestone.getNodeKey(), milestone.getCode(), nodeKeys, milestones, milestone);
            optionalReference(stages, milestone.getStageCode(), "里程碑阶段");
        }
        Map<String, TemplateExecutionSnapshot.DeliverableContract> deliverables = new LinkedHashMap<>();
        for (var deliverable : snapshot.getDeliverables()) {
            require(deliverable != null, "交付件为空");
            node(deliverable.getNodeKey(), deliverable.getCode(), nodeKeys, deliverables, deliverable);
            optionalReference(stages, deliverable.getStageCode(), "交付件阶段");
            optionalReference(tasks, deliverable.getTaskCode(), "交付件任务");
        }
        Map<String, TemplateExecutionSnapshot.GateContract> gates = new LinkedHashMap<>();
        for (var gate : snapshot.getGates()) {
            require(gate != null, "门禁为空");
            node(gate.getNodeKey(), gate.getCode(), nodeKeys, gates, gate);
            optionalReference(stages, gate.getStageCode(), "门禁阶段");
            String key = "$gate:" + gate.getNodeKey();
            require(key.equals(gate.getConditionRuleKey()) && expectedPrograms.add(key), "门禁程序身份错误");
            RuleProgram program = requireProgram(programs, key, VersionRule.Kind.CONDITION);
            require(gate.getReferences() != null && !gate.getReferences().isEmpty()
                    && program.leaves().size() == gate.getReferences().size(), "门禁引用不完整");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < gate.getReferences().size(); i++) {
                var ref = gate.getReferences().get(i);
                require(ref != null && text(ref.getRefType()) && text(ref.getRefCode())
                        && seen.add(ref.getRefType() + ":" + ref.getRefCode()), "门禁引用无效或重复");
                boolean exists = switch (ref.getRefType()) {
                    case "TASK" -> tasks.containsKey(ref.getRefCode());
                    case "MILESTONE" -> milestones.containsKey(ref.getRefCode());
                    case "DELIVERABLE" -> deliverables.containsKey(ref.getRefCode());
                    case "STATE" -> ref.getRefCode().endsWith("_COMPLETED")
                            && stages.containsKey(ref.getRefCode().substring(0, ref.getRefCode().length() - "_COMPLETED".length()));
                    case "APPROVAL", "PROCESS" -> text(ref.getRefVersion());
                    default -> false;
                };
                require(exists && ref.getRefType().equals(program.leaves().get(i).predicate())
                        && ref.getRefCode().equals(program.leaves().get(i).parameters().path("refCode").asText()), "门禁程序与引用不一致");
            }
        }
        for (var task : tasks.values()) optionalReference(gates, task.getGateRef(), "任务门禁");
        Set<String> edgeKeys = new HashSet<>(), edgeCodes = new HashSet<>();
        for (var edge : snapshot.getTransitions()) {
            require(edge != null && text(edge.getEdgeKey()) && edgeKeys.add(edge.getEdgeKey())
                    && text(edge.getCode()) && edgeCodes.add(edge.getCode()), "关系身份无效或重复");
            require(stages.containsKey(edge.getFromStageCode()) && stages.containsKey(edge.getToStageCode())
                    && !Objects.equals(edge.getFromStageCode(), edge.getToStageCode()), "关系引用无效");
            require(!text(edge.getConditionRuleKey()) && edge.getConditionRule() == null, "连线条件必须放在目标准入规则");
        }
        require(expectedPrograms.equals(programs.keySet()), "冻结程序集合与规则/门禁不一致");
    }

    private static RuleProgram requireProgram(Map<String, RuleProgram> programs, String key, VersionRule.Kind kind) {
        RuleProgram program = programs.get(key);
        require(program != null && program.kind() == kind && text(program.el())
                && program.leaves() != null && !program.leaves().isEmpty(), "冻结程序缺失或类型错误: " + key);
        Set<String> leaves = new HashSet<>();
        for (var leaf : program.leaves()) {
            require(leaf != null && text(leaf.key()) && leaves.add(leaf.key()) && text(leaf.path()) && text(leaf.predicate())
                    && leaf.parameters() != null && leaf.parameters().isObject(), "冻结事实叶子缺失或重复: " + key);
            if ("DECISION".equals(leaf.predicate()) || "DECISION_VALUE".equals(leaf.predicate())) {
                JsonNode table = leaf.parameters().path("table");
                require(!leaf.parameters().has("ruleKey") && table.isObject() && table.path("key").isTextual()
                        && text(table.path("key").asText()) && table.path("decisionKey").isTextual()
                        && text(table.path("decisionKey").asText()) && table.path("xml").isTextual()
                        && text(table.path("xml").asText()) && table.path("inputFields").isObject(), "决策表闭包缺失: " + key);
            }
        }
        return program;
    }

    private static void verifyLeaves(JsonNode expression, String path, List<RuleProgram.Leaf> leaves, int[] index, boolean root) {
        if (expression.has("operator")) {
            int child = 0;
            for (JsonNode item : expression.path("rules")) verifyLeaves(item, path + ".rules[" + child++ + "]", leaves, index, false);
        } else {
            int i = index[0]++;
            require(i < leaves.size(), "条件程序缺少事实叶子");
            var leaf = leaves.get(i);
            require(("condition" + i).equals(leaf.key()) && path.equals(leaf.path())
                    && expression.path("predicate").asText().equals(leaf.predicate())
                    && expression.path("parameters").equals(leaf.parameters()), "条件程序与冻结表达式不一致");
        }
        if (root) require(index[0] == leaves.size(), "条件程序含额外事实叶子");
    }

    private static void nodeRules(Map<String, RuleProgram> programs, Map<String, VersionRule> rules,
                                  String admission, String completion, String exit, JsonNode expression) {
        condition(programs, admission); condition(programs, completion); condition(programs, exit);
        require(text(completion) && expression != null && expression.isObject()
                && TemplateRuleCollection.condition(rules, completion).equals(expression), "节点完成规则未完整冻结");
    }

    private static void binding(TemplateExecutionSnapshot.BindingContract binding, Map<String, RuleProgram> programs) {
        if (binding == null) return;
        require(text(binding.getType()) && DeliveryDefinitionPayloadValidator.BINDING_TYPES.contains(binding.getType()), "绑定类型无效");
        JsonNode frozen = binding.getOperationContract();
        if (frozen == null) return;
        require(frozen.isObject() && frozen.path("programs").isObject(), "操作规则程序缺失");
        ObjectNode authoring = ((ObjectNode) frozen).deepCopy();
        authoring.remove("programs");
        var declaration = TemplateOperationContractJson.readAuthoring(authoring);
        Set<String> references = new HashSet<>(), operations = new HashSet<>();
        for (var operation : declaration.operations()) {
            require(operations.add(operation.operationCode()), "操作重复");
            for (var check : List.of(operation.pre(), operation.post())) {
                if (!"RULE".equals(check.mode())) continue;
                condition(programs, check.ruleKey());
                references.add(check.ruleKey());
                require(JsonUtils.parseTree(JsonUtils.toJsonString(programs.get(check.ruleKey())))
                        .equals(frozen.path("programs").get(check.ruleKey())), "操作程序与版本内规则不一致");
            }
        }
        require(references.equals(new HashSet<>(frozen.path("programs").propertyNames())), "操作程序集合不完整");
    }

    private static void execution(JsonNode value, TemplateExecutionSnapshot.BindingContract binding) {
        if (value == null) return;
        var config = TemplateExecutionConfiguration.read(value);
        // 旧验收专用完成通道尚未接入证据校验，不能通过新增配置绕过正式完成条件。
        require(config.subscriptions().isEmpty() || binding == null
                || !"ACC".equals(binding.getTargetContextCode()) || !"AcceptanceActivity".equals(binding.getTargetObjectType()),
                "LEGACY_ACCEPTANCE_SUBSCRIPTION_UNSUPPORTED");
        if (config.presentation() != null) TemplatePresentationContract.validate(config.presentation(), binding);
        if (config.operations().isEmpty()) return;
        require(binding != null && binding.getOperationContract() != null, "独立操作缺少冻结运行绑定");
        ObjectNode authoring = ((ObjectNode) binding.getOperationContract()).deepCopy();
        authoring.remove("programs");
        var frozen = TemplateOperationContractJson.readAuthoring(authoring).operations();
        require(frozen.size() == config.operations().size(), "独立操作与运行绑定集合不一致");
        // 原运行契约会按动作编码排序；独立配置的展示顺序不应被当作动作身份。
        Set<String> declaredCodes = new HashSet<>();
        for (var declared : config.operations()) {
            require(declaredCodes.add(declared.operationCode()), "独立操作重复");
            var operation = frozen.stream().filter(item -> Objects.equals(item.operationCode(), declared.operationCode()))
                    .findFirst().orElse(null);
            require(operation != null, "独立操作缺少精确运行绑定");
            require(Objects.equals(declared.ownerContext(), binding.getTargetContextCode())
                    && Objects.equals(declared.entityType(), binding.getTargetObjectType())
                    && Objects.equals(declared.operationCode(), operation.operationCode())
                    && Objects.equals(declared.pre(), operation.pre()) && Objects.equals(declared.post(), operation.post()),
                    "独立操作与冻结运行契约不一致");
        }
    }

    private static void condition(Map<String, RuleProgram> programs, String key) {
        if (key != null) {
            require(text(key), "空规则引用不能代替未配置");
            requireProgram(programs, key, VersionRule.Kind.CONDITION);
        }
    }

    private static <T> void node(String key, String code, Set<String> keys, Map<String, T> codes, T value) {
        require(text(key) && keys.add(key) && text(code) && codes.putIfAbsent(code, value) == null, "节点身份无效或重复");
    }

    private static void optionalReference(Map<String, ?> nodes, String code, String field) {
        require(code == null || text(code) && nodes.containsKey(code), field + "引用缺失");
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException("VERSION_SNAPSHOT_INVALID: " + reason);
    }
}
