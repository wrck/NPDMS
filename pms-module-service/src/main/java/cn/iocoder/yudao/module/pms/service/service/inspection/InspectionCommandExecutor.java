package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** SRV owns command semantics; PLT atomically owns replay, audit and outbox storage. */
@Component
@RequiredArgsConstructor
public class InspectionCommandExecutor {
    private final PlatformCommandExecutionApi platform;

    @Builder
    public record Write<T>(InspectionRequestContext context, String operation, String scope,
                           Object input, Class<T> responseType, Supplier<T> action,
                           Function<T, String> resourceKey) { }

    public <T> T execute(Write<T> write) {
        var context = write.context();
        InspectionException.require(context.idempotencyKey() != null, "缺少幂等标识");
        var result = platform.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        context.tenantId(), "SRV:" + write.operation() + ":" + write.scope(),
                        context.actorId(), context.idempotencyKey()),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(write.input())), write.responseType(), write.action(),
                response -> new PlatformCommandExecutionApi.SuccessFacts(write.operation(), "SRV_INSPECTION",
                        write.resourceKey().apply(response), context.correlationId(),
                        JsonUtils.toJsonString(response), List.of()));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new InspectionException(InspectionException.Reason.IDEMPOTENCY_CONFLICT,
                    "该幂等标识已用于不同内容，请恢复原操作或创建新的操作");
        }
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null) {
            throw new InspectionException(InspectionException.Reason.IN_PROGRESS,
                    "操作仍在处理中，请保留原内容和幂等标识重试");
        }
        return result.response();
    }
}
