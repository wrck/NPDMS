package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.MaterialWithdrawal;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialWithdrawalQuery;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.TenantCallerContext;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
        if (materialId == null || materialId <= 0 || expectedVersion == null || expectedVersion < 0
                || expectedVersion == Long.MAX_VALUE || key == null || key.isBlank() || key.length() > 128
                || reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessContractException("DELIVERY_WITHDRAWAL_INVALID", "材料撤回参数不完整");
        }
        var caller = callers.require();
        if (!permissions.lockAndCheck(caller.tenantId(), caller.userId(), "pms:delivery:operate")) throw DeliveryOwnerAccess.denied();
        var observed = materials.selectById(materialId);
        if (observed == null || !Objects.equals(observed.getTenantId(), caller.tenantId())) throw DeliveryOwnerAccess.denied();
        owners.require(observed.getOwnerModule(), observed.getEntityType(), observed.getEntityId(), observed.getTypeCode(), true, true);
        var locked = materials.selectMaterialsForUpdate(new DeliveryMaterialIdLockQuery(caller.tenantId(), List.of(materialId)));
        if (locked.size() != 1) throw DeliveryOwnerAccess.denied();
        var current = locked.getFirst();
        if (!Objects.equals(observed.getOwnerModule(), current.getOwnerModule())
                || !Objects.equals(observed.getEntityType(), current.getEntityType())
                || !Objects.equals(observed.getEntityId(), current.getEntityId())
                || !Objects.equals(observed.getTypeCode(), current.getTypeCode())) throw DeliveryOwnerAccess.denied();
        String digest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(JsonUtils.toJsonString(
                List.of(materialId, expectedVersion, reason)));
        var scope = new PlatformCommandExecutionApi.IdempotencyScope(caller.tenantId(), "DELIVERY_MATERIAL_WITHDRAW", caller.userId(), key);
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
            String detail = JsonUtils.toJsonString(Map.of("materialId", materialId, "versionBefore", expectedVersion,
                    "versionAfter", response.version(), "statusBefore", "ACTIVE", "statusAfter", response.status(), "reason", reason));
            String eventId = UUID.randomUUID().toString();
            String event = JsonUtils.toJsonString(new DeliveryEventPublisher.DeliveryChangedMessage(eventId,
                    current.getOwnerModule(), current.getEntityType(), current.getEntityId(), current.getTypeCode(),
                    "MATERIAL_WITHDRAWN", response.status(), java.time.LocalDateTime.now()));
            return new PlatformCommandExecutionApi.SuccessFacts("DELIVERY_MATERIAL_WITHDRAW", "DeliveryMaterial", materialId.toString(),
                    key, detail, List.of(new PlatformCommandExecutionApi.BusinessEvent(eventId, DeliveryEventPublisher.EVENT_TYPE, event)));
        });
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw new BusinessContractException("IDEMPOTENCY_CONFLICT", "同一幂等键的撤回请求摘要不一致");
        if (result.response() == null || result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS)
            throw new BusinessContractException("OPERATION_IN_PROGRESS", "材料撤回操作正在执行");
        return result.response();
    }
}
