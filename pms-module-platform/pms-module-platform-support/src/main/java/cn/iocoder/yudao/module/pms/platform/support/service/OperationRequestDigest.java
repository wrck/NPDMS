package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * 统一请求摘要：幂等键相同但载荷不同必须拒绝。摘要覆盖操作身份、目标身份、
 * 输入（键排序后规范化）与并发依据；入口类别不进入摘要（同一意图经不同入口重放仍算同一载荷）。
 */
public final class OperationRequestDigest {

    private OperationRequestDigest() {
    }

    public static String of(BusinessOperationRequest request) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("operationCode", request.operationCode());
        canonical.put("operationVersion", request.operationVersion());
        canonical.put("targetRef", request.targetRef());
        canonical.put("ownerModule", request.ownerModule());
        canonical.put("entityType", request.entityType());
        canonical.put("input", request.input() == null ? Map.of() : new TreeMap<>(request.input()));
        canonical.put("concurrencyBasis", request.concurrencyBasis());
        String json = cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(canonical);
        return sha256(json);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
