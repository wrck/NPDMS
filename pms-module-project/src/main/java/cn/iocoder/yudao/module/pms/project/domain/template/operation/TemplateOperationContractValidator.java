package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Structural and reference validation, not an expression engine or runtime guard.
 * The caller supplies deployment-owned operation metadata and metadata produced
 * by the existing version-local rule compiler. Neither comes from browser grants.
 */
public final class TemplateOperationContractValidator {
    private TemplateOperationContractValidator() { }

    public enum Checkpoint { PRE, POST }
    public enum RuleKind { CONDITION, DECISION }

    /** Owner/object identity is the binding identity, not an operation-code prefix. */
    public record Binding(String type, String ownerContext, String objectType) { }

    /** Emitted only by a real, registered command adapter, never by template JSON. */
    public record OperationMetadata(String code, Integer version, String ownerContext,
                                    String objectType, Set<Checkpoint> checkpoints) {
        public OperationMetadata {
            checkpoints = checkpoints == null ? null : Set.copyOf(checkpoints);
        }
    }

    /**
     * A rule is usable at a checkpoint only when the existing compiler/adapter
     * can resolve every leaf there. In particular, POST must not await a later
     * event, another task's completion, or an unsupported local-result predicate.
     * An empty set declares no supported operation checkpoint; null is invalid.
     */
    public record RuleMetadata(RuleKind kind, Set<Checkpoint> availableAt) {
        public RuleMetadata {
            availableAt = availableAt == null ? null : Set.copyOf(availableAt);
        }
    }

    public record Issue(String path, String code, String message) { }

    /**
     * The absent contract remains null. referencedRuleKeys must be resolved to
     * exact compiled programs when freezing/hashing; keys alone are not evidence
     * that a changed rule has the same semantics.
     */
    public record Validation(TemplateOperationContract contract, List<Issue> issues,
                             List<String> referencedRuleKeys) {
        public Validation {
            issues = List.copyOf(issues);
            referencedRuleKeys = List.copyOf(referencedRuleKeys);
        }
        public boolean valid() { return issues.isEmpty(); }
        public boolean legacy() { return valid() && contract == null; }
    }

    public static Validation validate(TemplateOperationContract contract, Binding binding,
            List<OperationMetadata> deploymentOperations, Map<String, RuleMetadata> compiledRules,
            String path) {
        Objects.requireNonNull(path, "operation contract path");
        if (path.isBlank()) throw new IllegalArgumentException("operation contract path is blank");
        // Do not query registries or reinterpret old bindings when the field is absent.
        if (contract == null) return new Validation(null, List.of(), List.of());
        List<Issue> issues = new ArrayList<>();
        if (!Integer.valueOf(TemplateOperationContract.VERSION).equals(contract.version()))
            issue(issues, path + ".version", "OPERATION_CONTRACT_VERSION_UNSUPPORTED",
                    "操作子契约版本不受支持");
        if (binding == null || !Set.of("BUSINESS_OBJECT", "BUSINESS_COMPONENT").contains(
                String.valueOf(binding.type())) || blank(binding.ownerContext()) || blank(binding.objectType()))
            issue(issues, path, "OPERATION_BINDING_INVALID", "操作子契约需要明确的业务 Owner 和对象类型");

        Map<OperationKey, OperationMetadata> operations = operationIndex(deploymentOperations, path, issues);
        if (contract.operations() == null || contract.operations().isEmpty()) {
            issue(issues, path + ".operations", "OPERATIONS_REQUIRED", "新操作子契约必须明确声明操作");
            return new Validation(null, issues, List.of());
        }
        Set<String> codes = new HashSet<>();
        Set<String> referencedRules = new TreeSet<>();
        for (int i = 0; i < contract.operations().size(); i++) {
            var operation = contract.operations().get(i);
            String itemPath = path + ".operations[" + i + "]";
            if (operation == null) {
                issue(issues, itemPath, "OPERATION_REQUIRED", "操作不能为空");
                continue;
            }
            if (blank(operation.operationCode()))
                issue(issues, itemPath + ".operationCode", "OPERATION_CODE_REQUIRED", "操作编码不能为空");
            else if (!codes.add(operation.operationCode()))
                issue(issues, itemPath + ".operationCode", "OPERATION_DUPLICATE", "同一绑定不能重复声明操作");
            if (operation.operationVersion() == null || operation.operationVersion() < 1)
                issue(issues, itemPath + ".operationVersion", "OPERATION_VERSION_INVALID", "必须声明精确操作版本");
            var descriptor = operations.get(new OperationKey(operation.operationCode(), operation.operationVersion()));
            if (descriptor == null)
                issue(issues, itemPath, "OPERATION_NOT_DEPLOYED", "精确操作版本未登记或目录不可用");
            else if (binding != null && (!Objects.equals(binding.ownerContext(), descriptor.ownerContext())
                    || !Objects.equals(binding.objectType(), descriptor.objectType())))
                issue(issues, itemPath, "OPERATION_OWNER_MISMATCH", "操作与绑定的 Owner 或对象类型不一致");
            validateCheck(operation.pre(), Checkpoint.PRE, descriptor, compiledRules,
                    itemPath + ".pre", issues, referencedRules);
            validateCheck(operation.post(), Checkpoint.POST, descriptor, compiledRules,
                    itemPath + ".post", issues, referencedRules);
        }
        if (!issues.isEmpty()) return new Validation(null, issues, List.of());
        // Operation order is not execution order. Make the new sub-contract deterministic.
        var normalized = new TemplateOperationContract(contract.version(), contract.operations().stream()
                .sorted(Comparator.comparing(TemplateOperationContract.Operation::operationCode)).toList());
        return new Validation(normalized, issues, List.copyOf(referencedRules));
    }

    private static Map<OperationKey, OperationMetadata> operationIndex(
            List<OperationMetadata> descriptors, String path, List<Issue> issues) {
        Map<OperationKey, OperationMetadata> result = new HashMap<>();
        if (descriptors == null) {
            issue(issues, path, "OPERATION_CATALOG_UNAVAILABLE", "无法取得受信操作目录");
            return result;
        }
        for (var descriptor : descriptors) {
            if (descriptor == null || blank(descriptor.code()) || descriptor.version() == null
                    || descriptor.version() < 1 || blank(descriptor.ownerContext())
                    || blank(descriptor.objectType()) || descriptor.checkpoints() == null) {
                issue(issues, path, "OPERATION_CATALOG_INVALID", "操作目录元数据不完整");
                continue;
            }
            if (result.putIfAbsent(new OperationKey(descriptor.code(), descriptor.version()), descriptor) != null)
                issue(issues, path, "OPERATION_CATALOG_DUPLICATE", "操作目录存在重复版本登记");
        }
        return result;
    }

    private static void validateCheck(TemplateOperationContract.Check check, Checkpoint checkpoint,
            OperationMetadata descriptor, Map<String, RuleMetadata> rules, String path,
            List<Issue> issues, Set<String> referencedRules) {
        if (check == null) {
            issue(issues, path, "OPERATION_CHECK_REQUIRED", "必须明确声明 NONE 或 RULE");
            return;
        }
        if ("NONE".equals(check.mode())) {
            if (check.ruleKey() != null)
                issue(issues, path + ".ruleKey", "NONE_RULE_FORBIDDEN", "NONE 不能携带 ruleKey");
            return;
        }
        if (!"RULE".equals(check.mode())) {
            issue(issues, path + ".mode", "OPERATION_CHECK_MODE_INVALID", "仅支持 NONE 或 RULE");
            return;
        }
        if (descriptor != null && !descriptor.checkpoints().contains(checkpoint))
            issue(issues, path, "OPERATION_CHECKPOINT_UNSUPPORTED", "该操作未声明此检查点能力");
        if (blank(check.ruleKey())) {
            issue(issues, path + ".ruleKey", "OPERATION_RULE_REQUIRED", "RULE 必须携带 ruleKey");
            return;
        }
        if (rules == null) {
            issue(issues, path, "OPERATION_RULE_CATALOG_UNAVAILABLE", "无法读取版本内已编译规则");
            return;
        }
        var rule = rules.get(check.ruleKey());
        if (rule == null) {
            issue(issues, path + ".ruleKey", "OPERATION_RULE_NOT_FOUND", "规则不在当前版本的已编译规则集合中");
            return;
        }
        if (rule.kind() != RuleKind.CONDITION)
            issue(issues, path + ".ruleKey", "OPERATION_RULE_KIND_INVALID", "操作检查只能引用条件规则");
        if (rule.availableAt() == null || !rule.availableAt().contains(checkpoint))
            issue(issues, path + ".ruleKey", "OPERATION_RULE_FACT_UNAVAILABLE",
                    "规则包含当前检查点不可判定或不受支持的事实");
        referencedRules.add(check.ruleKey());
    }

    private record OperationKey(String code, Integer version) { }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void issue(List<Issue> issues, String path, String code, String message) {
        issues.add(new Issue(path, code, message));
    }
}
