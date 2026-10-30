package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContract;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractJson;
import cn.iocoder.yudao.module.pms.project.domain.template.operation.TemplateOperationContractValidator.Checkpoint;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateOperationCompilation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Reads only the compiler-owned frozen closure; never recompiles against a newer template. */
public record FrozenOperationContract(TemplateOperationContract declaration, Map<String, RuleProgram> programs) {
    public FrozenOperationContract { programs = Map.copyOf(programs); }

    public static FrozenOperationContract read(JsonNode value) {
        if (value == null) return null;
        if (!value.isObject() || !value.path("programs").isObject()) throw invalid();
        ObjectNode authoring = ((ObjectNode) value).deepCopy();
        var compiled = authoring.remove("programs");
        var contract = TemplateOperationContractJson.readAuthoring(authoring);
        if (!Integer.valueOf(1).equals(contract.version())) throw invalid();
        Map<String, RuleProgram> programs = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>(), references = new HashSet<>();
        for (var operation : contract.operations()) {
            if (!codes.add(operation.operationCode())) throw invalid();
            for (var slot : Map.of(Checkpoint.PRE, operation.pre(), Checkpoint.POST, operation.post()).entrySet()) {
                if (!"RULE".equals(slot.getValue().mode())) continue;
                String key = slot.getValue().ruleKey();
                var json = compiled.get(key);
                if (json == null || !json.isObject()) throw invalid();
                var program = JsonUtils.parseObject(JsonUtils.toJsonString(json), RuleProgram.class);
                if (!TemplateOperationCompilation.supports(program, slot.getKey())) throw invalid();
                programs.put(key, program); references.add(key);
            }
        }
        Set<String> stored = new HashSet<>();
        compiled.properties().forEach(entry -> stored.add(entry.getKey()));
        if (!stored.equals(references)) throw invalid();
        return new FrozenOperationContract(contract, programs);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("FROZEN_OPERATION_CONTRACT_INVALID"); }
}
