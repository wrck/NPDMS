package cn.iocoder.yudao.module.pms.cutover.service.support;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** 复用平台同事务幂等、审计能力，供配置及尚未接入该能力的割接命令使用。 */
@Component
@RequiredArgsConstructor
public class CutoverCommandExecutor {
    private final PlatformCommandExecutionApi platform;

    @Builder
    public record Write<T>(Long tenantId, Long actorId, String operationCode, String scopeKey,
                           String aggregateType, CutoverWriteIntent intent, Object businessInput,
                           Class<T> responseType, Supplier<T> operation, Function<T, String> resourceKey) { }

    public <T> T execute(Write<T> write) {
        var intent = write.intent();
        if (intent == null || !validText(intent.idempotencyKey()) || !validText(intent.correlationId())) {
            throw new CutoverCommandException(CutoverCommandException.Reason.INVALID_INTENT,
                    "Idempotency-Key 不能为空，且必须是最多 128 字符的单值标识");
        }
        var result = platform.execute(new PlatformCommandExecutionApi.IdempotencyScope(write.tenantId(),
                        "CUT:" + write.operationCode() + ":" + write.scopeKey(), write.actorId(), intent.idempotencyKey()),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(write.businessInput())), write.responseType(), write.operation(),
                response -> new PlatformCommandExecutionApi.SuccessFacts(write.operationCode(), write.aggregateType(),
                        write.resourceKey().apply(response), intent.correlationId(), JsonUtils.toJsonString(response), List.of()));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new CutoverCommandException(CutoverCommandException.Reason.IDEMPOTENCY_CONFLICT,
                    "同一幂等标识不能用于不同的操作内容");
        }
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null) {
            throw new CutoverCommandException(CutoverCommandException.Reason.IDEMPOTENCY_IN_PROGRESS,
                    "该操作仍在处理中，请使用原幂等标识重试");
        }
        return result.response();
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= 128;
    }
}
