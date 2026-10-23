package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.Builder;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** Identity is always obtained from the platform authenticated tenant context. */
@Builder
public record InspectionRequestContext(Long tenantId, Long actorId, String idempotencyKey,
                                       String correlationId, Long expectedVersion) {
    public static InspectionRequestContext current() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0) {
            throw new InspectionException(InspectionException.Reason.FORBIDDEN, "请先登录");
        }
        return new InspectionRequestContext(TenantContextHolder.getRequiredTenantId(), actor,
                null, UUID.randomUUID().toString(), null);
    }

    public static InspectionRequestContext write(HttpHeaders headers) {
        var current = current();
        String key = single(headers, "Idempotency-Key");
        require(key != null && !key.isBlank() && key.length() <= 128 && key.equals(key.trim()),
                "Idempotency-Key 必须是最多128字符的单值标识");
        String value = single(headers, "If-Match");
        Long version = null;
        if (value != null) {
            if (value.matches("\"[0-9]+\"")) value = value.substring(1, value.length() - 1);
            require(value.matches("[0-9]+"), "If-Match 必须是非负版本号");
            try { version = Long.valueOf(value); }
            catch (NumberFormatException invalid) { throw new InspectionException(
                    InspectionException.Reason.INVALID_REQUEST, "If-Match 版本号超出范围"); }
        }
        return new InspectionRequestContext(current.tenantId(), current.actorId(), key,
                current.correlationId(), version);
    }

    public long requireVersion() {
        require(expectedVersion != null, "缺少 If-Match，请刷新后重试");
        return expectedVersion;
    }

    private static String single(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        require(values == null || values.size() == 1, name + " 只能提供一次");
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
