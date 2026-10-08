package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.MaterialWithdrawal;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.NativeOwnerActionRequest;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialWithdrawalQuery;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.TenantCallerContext;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

/** Public CAS command; native projections and legacy HTTP withdrawal keep their existing contracts. */
@Service
@RequiredArgsConstructor
public class DeliveryMaterialWithdrawalService {
    private final DeliveryMaterialMapper materials;
    private final DeliveryOwnerAccess owners;
    private final TenantCallerContext callers;
    private final ExplicitPermissionApi permissions;
    private final PlatformCommandExecutionApi commands;

    @Transactional(rollbackFor = Exception.class)
    public MaterialWithdrawal withdraw(Long materialId, Long expectedVersion, String key, String reason) {
        return execute(null, materialId, expectedVersion, key, reason);
    }

    /** Joins the native state transition; it must never start a separate material-only transaction. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public MaterialWithdrawal withdrawForOwnerAction(NativeOwnerActionRequest action, Long materialId,
            Long expectedVersion, String key, String reason) {
        if (action == null || action.ownerModule() == null || action.ownerModule().isBlank()
                || action.entityType() == null || action.entityType().isBlank()
                || action.entityId() == null || action.entityId() <= 0 || action.action() == null
                || action.expectedOwnerVersion() == null || action.expectedOwnerVersion() < 0
                || action.expectedOwnerVersion() == Long.MAX_VALUE) {
            throw new BusinessContractException("DELIVERY_NATIVE_OWNER_ACTION_INVALID", "原生Owner动作与版本参数不完整");
        }
        return execute(action, materialId, expectedVersion, key, reason);
    }

    private MaterialWithdrawal execute(NativeOwnerActionRequest action, Long materialId,
            Long expectedVersion, String key, String reason) {
        if (materialId == null || materialId <= 0 || expectedVersion == null || expectedVersion < 0
                || expectedVersion == Long.MAX_VALUE || key == null || key.isBlank() || key.length() > 128
                || reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessContractException("DELIVERY_WITHDRAWAL_INVALID", "材料撤回参数不完整");
        }
        var caller = callers.require();
        if (action == null && !permissions.lockAndCheck(caller.tenantId(), caller.userId(), "pms:delivery:operate"))
            throw DeliveryOwnerAccess.denied();
        var observed = materials.selectById(materialId);
        if (observed == null || !Objects.equals(observed.getTenantId(), caller.tenantId())) throw DeliveryOwnerAccess.denied();
        if (action == null) {
            owners.require(observed.getOwnerModule(), observed.getEntityType(), observed.getEntityId(), observed.getTypeCode(), true, true);
        } else {
            if (!Objects.equals(action.ownerModule(), observed.getOwnerModule())
                    || !Objects.equals(action.entityType(), observed.getEntityType())
                    || !Objects.equals(action.entityId(), observed.getEntityId())) throw DeliveryOwnerAccess.denied();
            owners.requireNativeOwnerAction(caller.tenantId(), caller.userId(), action, observed.getTypeCode());
        }
        var locked = materials.selectMaterialsForUpdate(new DeliveryMaterialIdLockQuery(caller.tenantId(), List.of(materialId)));
        if (locked.size() != 1) throw DeliveryOwnerAccess.denied();
        var current = locked.getFirst();
        if (!Objects.equals(observed.getOwnerModule(), current.getOwnerModule())
                || !Objects.equals(observed.getEntityType(), current.getEntityType())
                || !Objects.equals(observed.getEntityId(), current.getEntityId())
                || !Objects.equals(observed.getTypeCode(), current.getTypeCode())) throw DeliveryOwnerAccess.denied();
        String operation = action == null ? "DELIVERY_MATERIAL_WITHDRAW" : "DELIVERY_MATERIAL_OWNER_" + action.action().name();
        var requestFacts = action == null ? List.of(materialId, expectedVersion, reason)
                : List.of(action.ownerModule(), action.entityType(), action.entityId(), action.expectedOwnerVersion(),
                        action.action().name(), materialId, expectedVersion, reason);
        String digest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(JsonUtils.toJsonString(requestFacts));
        var scope = new PlatformCommandExecutionApi.IdempotencyScope(caller.tenantId(), operation, caller.userId(), key);
        var result = commands.execute(scope, digest, MaterialWithdrawal.class, () -> {
            if (current.getRequirementId() != null || current.getArchiveTime() != null
                    || current.getArchiveStatus() != null && !DeliveryMaterialDO.ARCHIVE_NOT_REQUIRED.equals(current.getArchiveStatus())) {
                throw new BusinessContractException("DELIVERY_OWNER_COMMAND_REQUIRED", "模板或归档材料请使用来源Owner操作接口");
            }
            if (materials.withdrawIfVersion(new DeliveryMaterialWithdrawalQuery(caller.tenantId(), materialId,
                    expectedVersion, caller.userId().toString())) != 1) {
                throw new BusinessContractException("DELIVERY_MATERIAL_VERSION_CONFLICT", "材料状态、版本或使用关系已变化");
            }
            return new MaterialWithdrawal(materialId, expectedVersion + 1, DeliveryMaterialDO.STATUS_WITHDRAWN);
        }, response -> {
            var detailFacts = new LinkedHashMap<String, Object>(Map.of("materialId", materialId, "versionBefore", expectedVersion,
                    "versionAfter", response.version(), "statusBefore", "ACTIVE", "statusAfter", response.status(), "reason", reason));
            if (action != null) {
                detailFacts.put("nativeOwnerAction", action.action().name());
                detailFacts.put("nativeOwnerVersionBefore", action.expectedOwnerVersion());
            }
            String detail = JsonUtils.toJsonString(detailFacts);
            String eventId = UUID.randomUUID().toString();
            String event = JsonUtils.toJsonString(new DeliveryEventPublisher.DeliveryChangedMessage(eventId,
                    current.getOwnerModule(), current.getEntityType(), current.getEntityId(), current.getTypeCode(),
                    "MATERIAL_WITHDRAWN", response.status(), java.time.LocalDateTime.now()));
            return new PlatformCommandExecutionApi.SuccessFacts(operation, "DeliveryMaterial", materialId.toString(),
                    key, detail, List.of(new PlatformCommandExecutionApi.BusinessEvent(eventId, DeliveryEventPublisher.EVENT_TYPE, event)));
        });
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw new BusinessContractException("IDEMPOTENCY_CONFLICT", "同一幂等键的撤回请求摘要不一致");
        if (result.response() == null || result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS)
            throw new BusinessContractException("OPERATION_IN_PROGRESS", "材料撤回操作正在执行");
        return result.response();
    }
}
