package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractValidator.*;

/** Same scenarios are executable with the JDK and through the JUnit bridge. */
public final class TemplateOperationContractCases {
    private static final String PATH = "tasks[0].workBinding.operationContract";
    private static final Binding BINDING = new Binding("BUSINESS_OBJECT", "SOL", "SITE_SURVEY");
    private static final String CODE = "SOL.SITE_SURVEY.CONFIRM";
    private static final TemplateOperationContract.Check NONE = new TemplateOperationContract.Check("NONE", null);
    private static final TemplateOperationContract.Check PRE_RULE = new TemplateOperationContract.Check("RULE", "ready");
    private static final TemplateOperationContract.Check POST_RULE = new TemplateOperationContract.Check("RULE", "confirmed");
    private static final OperationMetadata OP = new OperationMetadata(CODE, 1, "SOL", "SITE_SURVEY", Set.of(Checkpoint.PRE, Checkpoint.POST));
    private static final Map<String, RuleMetadata> RULES = Map.of(
            "ready", new RuleMetadata(RuleKind.CONDITION, Set.of(Checkpoint.PRE)),
            "confirmed", new RuleMetadata(RuleKind.CONDITION, Set.of(Checkpoint.POST)));

    private TemplateOperationContractCases() { }

    public static List<String> runAll() {
        List<String> passed = new ArrayList<>();
        check(passed, "absent contract preserves legacy without a catalog", () -> {
            var result = validate(null, null, null, null, PATH);
            require(result.valid() && result.legacy() && result.contract() == null, "legacy path was changed");
        });
        check(passed, "explicit NONE does not need rule metadata", () ->
                valid(validate(contract(NONE, NONE), BINDING, List.of(OP), null, PATH)));
        check(passed, "PRE and POST use their own supported facts", () -> valid(run(contract(PRE_RULE, POST_RULE))));
        check(passed, "unknown sub-contract version rejected", () ->
                error(run(new TemplateOperationContract(2, contract(NONE, NONE).operations())), "OPERATION_CONTRACT_VERSION_UNSUPPORTED"));
        check(passed, "null version rejected", () -> error(run(new TemplateOperationContract(null, contract(NONE, NONE).operations())), "OPERATION_CONTRACT_VERSION_UNSUPPORTED"));
        check(passed, "missing operations rejected", () -> error(run(new TemplateOperationContract(1, null)), "OPERATIONS_REQUIRED"));
        check(passed, "empty operations rejected", () -> error(run(new TemplateOperationContract(1, List.of())), "OPERATIONS_REQUIRED"));
        check(passed, "null operation reports a field error", () -> error(run(new TemplateOperationContract(1, Arrays.asList((TemplateOperationContract.Operation) null))), "OPERATION_REQUIRED"));
        check(passed, "duplicate operation rejected", () -> {
            var operation = contract(NONE, NONE).operations().getFirst();
            error(run(new TemplateOperationContract(1, List.of(operation, operation))), "OPERATION_DUPLICATE");
        });
        check(passed, "missing PRE not converted to NONE", () -> error(run(contract(null, NONE)), "OPERATION_CHECK_REQUIRED"));
        check(passed, "missing POST not converted to NONE", () -> error(run(contract(NONE, null)), "OPERATION_CHECK_REQUIRED"));
        check(passed, "unknown mode rejected", () -> error(run(contract(new TemplateOperationContract.Check("ALLOW", null), NONE)), "OPERATION_CHECK_MODE_INVALID"));
        check(passed, "blank mode rejected", () -> error(run(contract(new TemplateOperationContract.Check(" ", null), NONE)), "OPERATION_CHECK_MODE_INVALID"));
        check(passed, "NONE with rule key rejected", () -> error(run(contract(new TemplateOperationContract.Check("NONE", "ready"), NONE)), "NONE_RULE_FORBIDDEN"));
        check(passed, "NONE with empty key rejected", () -> error(run(contract(new TemplateOperationContract.Check("NONE", ""), NONE)), "NONE_RULE_FORBIDDEN"));
        check(passed, "RULE without key rejected", () -> error(run(contract(new TemplateOperationContract.Check("RULE", null), NONE)), "OPERATION_RULE_REQUIRED"));
        check(passed, "RULE with blank key rejected", () -> error(run(contract(new TemplateOperationContract.Check("RULE", " "), NONE)), "OPERATION_RULE_REQUIRED"));
        check(passed, "nonexistent rule rejected", () -> error(run(contract(new TemplateOperationContract.Check("RULE", "foreign-version"), NONE)), "OPERATION_RULE_NOT_FOUND"));
        check(passed, "no latest-rule fallback", () -> error(run(contract(new TemplateOperationContract.Check("RULE", " ready "), NONE)), "OPERATION_RULE_NOT_FOUND"));
        check(passed, "missing rule catalog fails closed", () -> error(validate(contract(PRE_RULE, NONE), BINDING, List.of(OP), null, PATH), "OPERATION_RULE_CATALOG_UNAVAILABLE"));
        check(passed, "decision rule cannot replace condition", () -> error(validate(contract(PRE_RULE, NONE), BINDING, List.of(OP), Map.of("ready", new RuleMetadata(RuleKind.DECISION, Set.of(Checkpoint.PRE))), PATH), "OPERATION_RULE_KIND_INVALID"));
        check(passed, "unknown rule kind rejected", () -> error(validate(contract(PRE_RULE, NONE), BINDING, List.of(OP), Map.of("ready", new RuleMetadata(null, Set.of(Checkpoint.PRE))), PATH), "OPERATION_RULE_KIND_INVALID"));
        check(passed, "POST cannot consume PRE-only facts", () -> error(run(contract(NONE, PRE_RULE)), "OPERATION_RULE_FACT_UNAVAILABLE"));
        check(passed, "PRE cannot consume future operation result", () -> error(run(contract(POST_RULE, NONE)), "OPERATION_RULE_FACT_UNAVAILABLE"));
        check(passed, "async-only facts rejected at POST", () -> error(validate(contract(NONE, POST_RULE), BINDING, List.of(OP), Map.of("confirmed", new RuleMetadata(RuleKind.CONDITION, Set.of())), PATH), "OPERATION_RULE_FACT_UNAVAILABLE"));
        check(passed, "missing fact capability is not permission", () -> error(validate(contract(NONE, POST_RULE), BINDING, List.of(OP), Map.of("confirmed", new RuleMetadata(RuleKind.CONDITION, null)), PATH), "OPERATION_RULE_FACT_UNAVAILABLE"));
        check(passed, "undeployed exact operation rejected", () -> error(validate(contract(NONE, NONE), BINDING, List.of(), RULES, PATH), "OPERATION_NOT_DEPLOYED"));
        check(passed, "catalog unavailable rejected even for NONE", () -> error(validate(contract(NONE, NONE), BINDING, null, RULES, PATH), "OPERATION_CATALOG_UNAVAILABLE"));
        check(passed, "newer operation version not chosen implicitly", () -> error(validate(contract(NONE, NONE), BINDING, List.of(new OperationMetadata(CODE, 2, "SOL", "SITE_SURVEY", OP.checkpoints())), RULES, PATH), "OPERATION_NOT_DEPLOYED"));
        check(passed, "missing operation version rejected", () -> error(run(single(new TemplateOperationContract.Operation(CODE, null, NONE, NONE))), "OPERATION_VERSION_INVALID"));
        check(passed, "invalid operation version rejected", () -> error(run(single(new TemplateOperationContract.Operation(CODE, 0, NONE, NONE))), "OPERATION_VERSION_INVALID"));
        check(passed, "blank operation code rejected", () -> error(run(single(new TemplateOperationContract.Operation(" ", 1, NONE, NONE))), "OPERATION_CODE_REQUIRED"));
        check(passed, "native binding cannot gain business operations", () -> error(validate(contract(NONE, NONE), new Binding("TASK_NATIVE", "SOL", "SITE_SURVEY"), List.of(OP), RULES, PATH), "OPERATION_BINDING_INVALID"));
        check(passed, "missing binding rejected", () -> error(validate(contract(NONE, NONE), null, List.of(OP), RULES, PATH), "OPERATION_BINDING_INVALID"));
        check(passed, "component binding accepted for matching Owner", () -> valid(validate(contract(NONE, NONE), new Binding("BUSINESS_COMPONENT", "SOL", "SITE_SURVEY"), List.of(OP), RULES, PATH)));
        check(passed, "different Owner rejected", () -> error(validate(contract(NONE, NONE), new Binding("BUSINESS_OBJECT", "ACC", "SITE_SURVEY"), List.of(OP), RULES, PATH), "OPERATION_OWNER_MISMATCH"));
        check(passed, "different entity rejected", () -> error(validate(contract(NONE, NONE), new Binding("BUSINESS_OBJECT", "SOL", "REQUIREMENT_ANALYSIS"), List.of(OP), RULES, PATH), "OPERATION_OWNER_MISMATCH"));
        check(passed, "unsupported operation checkpoint rejected", () -> error(validate(contract(PRE_RULE, POST_RULE), BINDING, List.of(new OperationMetadata(CODE, 1, "SOL", "SITE_SURVEY", Set.of(Checkpoint.PRE))), RULES, PATH), "OPERATION_CHECKPOINT_UNSUPPORTED"));
        check(passed, "NONE does not require an evaluation hook", () -> valid(validate(contract(NONE, NONE), BINDING, List.of(new OperationMetadata(CODE, 1, "SOL", "SITE_SURVEY", Set.of())), null, PATH)));
        check(passed, "duplicate catalog entries rejected", () -> error(validate(contract(NONE, NONE), BINDING, List.of(OP, OP), RULES, PATH), "OPERATION_CATALOG_DUPLICATE"));
        check(passed, "null catalog entry rejected", () -> error(validate(contract(NONE, NONE), BINDING, Arrays.asList(OP, null), RULES, PATH), "OPERATION_CATALOG_INVALID"));
        check(passed, "malformed catalog entry rejected", () -> error(validate(contract(NONE, NONE), BINDING, List.of(new OperationMetadata(CODE, 1, "SOL", "SITE_SURVEY", null)), RULES, PATH), "OPERATION_CATALOG_INVALID"));
        check(passed, "multiple deployed versions require exact choice", () -> valid(validate(contract(NONE, NONE), BINDING, List.of(new OperationMetadata(CODE, 2, "SOL", "SITE_SURVEY", OP.checkpoints()), OP), RULES, PATH)));
        check(passed, "stage and task use the same semantics", () -> {
            var task = run(contract(PRE_RULE, POST_RULE));
            var stage = validate(contract(PRE_RULE, POST_RULE), BINDING, List.of(OP), RULES, "stages[0].workBinding.operationContract");
            require(task.equals(stage), "valid stage and task results differ");
        });
        check(passed, "nested error points to the exact check", () -> {
            var result = run(contract(null, NONE));
            require(result.issues().stream().anyMatch(issue -> issue.path().equals(PATH + ".operations[0].pre")), "field path missing");
        });
        check(passed, "invalid contract is not a legacy contract", () -> {
            var result = run(contract(null, NONE));
            require(!result.valid() && !result.legacy() && result.contract() == null && result.referencedRuleKeys().isEmpty(), "invalid input was exposed as usable");
        });
        check(passed, "source list cannot mutate the snapshot", () -> {
            var items = new ArrayList<>(contract(NONE, NONE).operations());
            var value = new TemplateOperationContract(1, items);
            items.clear();
            require(value.operations().size() == 1, "list leaked into contract");
            throwsUnsupported(() -> value.operations().clear());
        });
        check(passed, "descriptor capability sets are copied", () -> {
            var phases = new HashSet<>(Set.of(Checkpoint.PRE));
            var descriptor = new OperationMetadata(CODE, 1, "SOL", "SITE_SURVEY", phases);
            phases.clear();
            require(descriptor.checkpoints().contains(Checkpoint.PRE), "descriptor set mutated");
        });
        check(passed, "rule references are distinct and deterministic", () -> {
            var result = run(contract(PRE_RULE, POST_RULE));
            require(result.referencedRuleKeys().equals(List.of("confirmed", "ready")), "reference order differs");
            throwsUnsupported(() -> result.referencedRuleKeys().clear());
        });
        check(passed, "operation ordering is canonical and source unchanged", () -> {
            var update = new TemplateOperationContract.Operation("SOL.SITE_SURVEY.UPDATE", 1, NONE, NONE);
            var confirm = contract(NONE, NONE).operations().getFirst();
            var source = new TemplateOperationContract(1, List.of(update, confirm));
            var descriptors = List.of(OP, new OperationMetadata(update.operationCode(), 1, "SOL", "SITE_SURVEY", Set.of()));
            var first = validate(source, BINDING, descriptors, RULES, PATH);
            var second = validate(new TemplateOperationContract(1, List.of(confirm, update)), BINDING, descriptors, RULES, PATH);
            require(first.equals(second), "reordering changed normalized contract");
            require(source.operations().getFirst().equals(update), "source was rewritten");
        });
        return List.copyOf(passed);
    }

    public static void main(String[] args) {
        var passed = runAll();
        passed.forEach(name -> System.out.println("PASS " + name));
        System.out.println("TOTAL " + passed.size() + " passed; 0 failed");
    }

    private static TemplateOperationContract contract(TemplateOperationContract.Check pre, TemplateOperationContract.Check post) {
        return single(new TemplateOperationContract.Operation(CODE, 1, pre, post));
    }
    private static TemplateOperationContract single(TemplateOperationContract.Operation value) {
        return new TemplateOperationContract(1, List.of(value));
    }
    private static Validation run(TemplateOperationContract value) { return validate(value, BINDING, List.of(OP), RULES, PATH); }
    private static void valid(Validation result) { require(result.valid() && !result.legacy(), "expected valid new contract: " + result.issues()); }
    private static void error(Validation result, String code) {
        require(!result.valid() && result.issues().stream().anyMatch(issue -> issue.code().equals(code)), "missing " + code + ": " + result.issues());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void throwsUnsupported(Runnable action) {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("collection must be immutable");
    }
    private static void check(List<String> passed, String name, Runnable action) {
        try { action.run(); } catch (Throwable failure) { throw new AssertionError(name, failure); }
        passed.add(name);
    }
}
