package cn.iocoder.yudao.module.pms.acceptance.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Reuses the exact published template schema; a terminal graph alone never authorizes closure. */
public record NormalClosurePolicy(String closureType, int ruleRevision, boolean requireTerminalStage,
                                  boolean requireAllTasksDone, boolean revalidateBusinessFacts,
                                  String processDefinitionKey, Long reviewerUserId) {
    public static final String PROCESS_KEY = cn.iocoder.yudao.module.pms.project.api.closure.ClosurePolicy.PROCESS_DEFINITION_KEY;

    public static NormalClosurePolicy parseFrozen(String snapshot) {
        if (snapshot == null || snapshot.isBlank() || "null".equals(snapshot.trim()))
            throw NormalClosureErrors.failure("CLOSURE_POLICY_NOT_FROZEN");
        try {
            var policy = new cn.iocoder.yudao.module.pms.project.api.closure.ClosurePolicy(JsonUtils.parseObject(snapshot, JsonNode.class));
            return new NormalClosurePolicy(policy.getClosureType(), policy.getRuleRevision(), policy.getRequireTerminalStage(),
                    policy.getRequireAllTasksDone(), policy.getRevalidateBusinessFacts(), policy.getProcessDefinitionKey(), policy.getReviewerUserId());
        } catch (RuntimeException ex) {
            throw NormalClosureErrors.failure("CLOSURE_POLICY_UNSUPPORTED");
        }
    }

    /** 闭环幂等证据摘要；与评估侧 digest 算法保持一致（SHA-256 over UTF-8）。 */
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    /** 证据规范化：对象键排序、数组保序；与 PROJ 评估侧 canonical 算法一致（纯函数，无漂移）。 */
    public static String canonical(JsonNode node) {
        if (node.isObject()) {
            List<String> keys = new java.util.ArrayList<>(node.propertyNames()); java.util.Collections.sort(keys);
            return "{" + keys.stream().map(key -> JsonUtils.toJsonString(key) + ":" + canonical(node.get(key)))
                    .collect(java.util.stream.Collectors.joining(",")) + "}";
        }
        if (node.isArray()) {
            List<String> items = new java.util.ArrayList<>(); node.forEach(item -> items.add(canonical(item)));
            return "[" + String.join(",", items) + "]";
        }
        return node.toString();
    }
}
