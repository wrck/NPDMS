package cn.iocoder.yudao.module.pms.project.service.platform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.platform.PlatformOperationAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectOperationAuditService {
    private final PlatformOperationAuditMapper auditMapper;

    public void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                       Long requestId, String resultCode, Map<String, ?> safeDetail) {
        record(tenantId, actorId, correlationId, operationCode, "ProjectSplitRequest",
                String.valueOf(requestId), resultCode, safeDetail);
    }

    public void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                       String aggregateType, String aggregateKey, String resultCode,
                       Map<String, ?> safeDetail) {
        PlatformOperationAuditDO audit = new PlatformOperationAuditDO();
        audit.setTenantId(tenantId);
        audit.setOperationCode(operationCode);
        audit.setAggregateType(aggregateType);
        audit.setAggregateKey(aggregateKey);
        audit.setActorId(actorId);
        audit.setCorrelationId(correlationId);
        audit.setIdempotencyKeyDigest(sha256(correlationId));
        audit.setResultCode(resultCode);
        audit.setDetailSnapshot(JsonUtils.toJsonString(safeDetail));
        audit.setOccurredAt(LocalDateTime.now());
        audit.setCreateTime(audit.getOccurredAt());
        if (auditMapper.insert(audit) != 1) {
            throw new IllegalStateException("项目拆分操作审计写入失败");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
