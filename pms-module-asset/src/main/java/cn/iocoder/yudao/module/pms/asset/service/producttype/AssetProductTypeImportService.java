package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.DeviceCurrentProductTypeInput;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.RecordAssetProductTypeSourceFailureCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;

@Service
@RequiredArgsConstructor
public class AssetProductTypeImportService {

    public static final String CONTROLLED_IMPORT_PERMISSION = "pms:asset-product-type:controlled-import";
    public static final String IMPORT_SCOPE = "AST:ASSET_PRODUCT_TYPE:CONTROLLED_IMPORT";
    public static final String IMPORT_OPERATION = "ASSET_PRODUCT_TYPE_CONTROLLED_IMPORT";

    private final SecurityFrameworkService securityFrameworkService;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final AssetProductTypeImportWriter importWriter;
    private final AssetProductTypeSourceFailureWriter sourceFailureWriter;
    private final AssetProductTypeConflictRecordService conflictRecordService;
    private final AssetProductTypeAuditService auditService;

    public ImportAssetProductTypeResult importProductType(ImportAssetProductTypeCommand command) {
        Actor actor = requireActor();
        validateCommand(command);
        String requestDigest = requestDigest(actor.tenantId(), command);
        try {
            PlatformCommandExecutionApi.ExecutionResult<ImportAssetProductTypeResult> execution =
                    commandExecutionApi.execute(
                            new PlatformCommandExecutionApi.IdempotencyScope(
                                    actor.tenantId(), IMPORT_SCOPE, actor.actorId(), command.idempotencyKey()),
                            requestDigest, ImportAssetProductTypeResult.class,
                            () -> importWriter.importOnce(actor.tenantId(), actor.actorId(), command),
                            result -> successFacts(command, result));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                    || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || execution.response() == null) {
                String rejectionCode = execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                        ? "IDEMPOTENCY_CONFLICT" : "IDEMPOTENCY_IN_PROGRESS";
                auditService.recordRejected(actor.tenantId(), actor.actorId(), command.operationId(), rejectionCode,
                        sha256(command.sourceSystem() + ":" + command.sourceKey()),
                        Map.of("sourceKeyDigest", sha256(command.sourceSystem() + ":" + command.sourceKey()),
                                "productTypeCodeDigest", sha256(command.productTypeCode()),
                                "rejectionCode", rejectionCode));
                throw exception(AST_PRODUCT_TYPE_IDEMPOTENCY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED) {
                ImportAssetProductTypeResult result = execution.response();
                return new ImportAssetProductTypeResult(result.productTypeId(), result.sourceMappingId(),
                        result.productTypeCode(), true);
            }
            return execution.response();
        } catch (AssetProductTypeImportRejectedException rejection) {
            if (rejection.conflict()) {
                conflictRecordService.record(actor.tenantId(), actor.actorId(), rejection);
            } else {
                auditService.recordRejected(actor.tenantId(), actor.actorId(), command.operationId(),
                        rejection.rejectionCode(), sha256(command.sourceSystem() + ":" + command.sourceKey()),
                        auditService.rejectionDetail(rejection));
            }
            throw exception(rejection.errorCode());
        }
    }

    public void recordSourceFailure(RecordAssetProductTypeSourceFailureCommand command) {
        Actor actor = requireActor();
        if (command == null || isBlank(command.operationId()) || isBlank(command.sourceSystem())
                || isBlank(command.sourceKey()) || isBlank(command.failureCode())) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        sourceFailureWriter.markFailed(actor.tenantId(), actor.actorId(), command);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            ImportAssetProductTypeCommand command, ImportAssetProductTypeResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("productTypeId", result.productTypeId());
        detail.put("sourceMappingId", result.sourceMappingId());
        detail.put("productTypeCodeDigest", sha256(result.productTypeCode()));
        detail.put("sourceKeyDigest", sha256(command.sourceSystem() + ":" + command.sourceKey()));
        detail.put("sourceVersion", command.sourceVersion());
        detail.put("sourceUpdatedAt", command.sourceUpdatedAt());
        detail.put("enabled", command.enabled());
        detail.put("deviceCount", command.devices().size());
        return new PlatformCommandExecutionApi.SuccessFacts(
                IMPORT_OPERATION, "AssetProductType", String.valueOf(result.productTypeId()),
                command.operationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private Actor requireActor() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || !securityFrameworkService.hasPermission(CONTROLLED_IMPORT_PERMISSION)) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getTenantId() == null || !loginUser.getTenantId().equals(tenantId)) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        return new Actor(tenantId, actorId);
    }

    private void validateCommand(ImportAssetProductTypeCommand command) {
        if (command == null || isBlank(command.operationId()) || isBlank(command.idempotencyKey())
                || isBlank(command.productTypeCode()) || isBlank(command.displayName())
                || isBlank(command.sourceSystem()) || isBlank(command.sourceKey())
                || isBlank(command.sourceVersion()) || command.sourceUpdatedAt() == null
                || command.payloadHash() == null || !command.payloadHash().matches("[0-9a-fA-F]{64}")) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
    }

    private String requestDigest(Long tenantId, ImportAssetProductTypeCommand command) {
        List<Map<String, Object>> devices = command.devices().stream()
                .sorted(Comparator.comparing(DeviceCurrentProductTypeInput::deviceId))
                .map(input -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("deviceId", input.deviceId());
                    item.put("resolutionStatus", input.resolutionStatus());
                    return item;
                }).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("productTypeCode", command.productTypeCode());
        payload.put("displayName", command.displayName());
        payload.put("enabled", command.enabled());
        payload.put("sourceSystem", command.sourceSystem());
        payload.put("sourceKey", command.sourceKey());
        payload.put("sourceVersion", command.sourceVersion());
        payload.put("sourceUpdatedAt", command.sourceUpdatedAt());
        payload.put("payloadHash", command.payloadHash().toLowerCase());
        payload.put("devices", devices);
        return sha256(JsonUtils.toJsonString(payload));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Actor(Long tenantId, Long actorId) {
    }
}
