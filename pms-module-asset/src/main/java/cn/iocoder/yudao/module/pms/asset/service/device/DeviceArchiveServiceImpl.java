package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchivePageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchiveStatusChangeReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceVersionMapper;
import cn.iocoder.yudao.module.pms.asset.domain.device.DeviceStatusRules;
import cn.iocoder.yudao.module.pms.asset.enums.DeviceArchiveStatusEnum;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_CUSTOMER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_SCRAPPED;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_SERIAL_NUMBER_DUPLICATE;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_STATUS_INVALID;

/**
 * 设备档案管理 Service 实现（ast_device 承载，自 pms_equipment 旧链承接）。
 * <p>
 * SN 租户内唯一；状态变更通过 {@link DeviceStatusRules} 状态机校验；
 * 每次创建/修改/状态变更追加一条 {@code ast_device_version} 记录（追加只读）。
 */
@Service
@RequiredArgsConstructor
public class DeviceArchiveServiceImpl implements DeviceArchiveService {

    private final DeviceMapper deviceMapper;
    private final DeviceVersionMapper deviceVersionMapper;
    private final CustomerQueryApi customerQueryApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDevice(DeviceArchiveSaveReqVO createReqVO) {
        validateCustomerAvailable(createReqVO.getCustomerId());
        validateSnUnique(null, createReqVO.getSn());
        DeviceDO entity = new DeviceDO();
        entity.setSn(createReqVO.getSn());
        entity.setName(createReqVO.getName());
        entity.setProductModel(createReqVO.getProductModel());
        entity.setCustomerId(createReqVO.getCustomerId());
        entity.setProjectId(createReqVO.getProjectId());
        entity.setWarrantyStartDate(createReqVO.getWarrantyStartDate());
        entity.setWarrantyEndDate(createReqVO.getWarrantyEndDate());
        entity.setRemark(createReqVO.getRemark());
        entity.setStatus(DeviceArchiveStatusEnum.IN_STOCK);
        deviceMapper.insert(entity);
        appendVersion(entity.getId(), null, entity, "CREATE", "创建设备档案");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDevice(DeviceArchiveSaveReqVO updateReqVO) {
        DeviceDO existing = validateDeviceExists(updateReqVO.getId());
        if (DeviceArchiveStatusEnum.RETIRED.equals(existing.getStatus())) {
            throw exception(AST_EQUIPMENT_SCRAPPED);
        }
        if (!Objects.equals(existing.getCustomerId(), updateReqVO.getCustomerId())) {
            validateCustomerAvailable(updateReqVO.getCustomerId());
        }
        validateSnUnique(updateReqVO.getId(), updateReqVO.getSn());
        DeviceDO update = new DeviceDO();
        update.setId(updateReqVO.getId());
        update.setSn(updateReqVO.getSn());
        update.setName(updateReqVO.getName());
        update.setProductModel(updateReqVO.getProductModel());
        update.setCustomerId(updateReqVO.getCustomerId());
        update.setWarrantyStartDate(updateReqVO.getWarrantyStartDate());
        update.setWarrantyEndDate(updateReqVO.getWarrantyEndDate());
        update.setRemark(updateReqVO.getRemark());
        deviceMapper.updateById(update);
        DeviceDO after = deviceMapper.selectById(updateReqVO.getId());
        appendVersion(updateReqVO.getId(), existing, after, "UPDATE", "更新设备档案");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long id) {
        validateDeviceExists(id);
        deviceMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeDeviceStatus(Long id, DeviceArchiveStatusChangeReqVO reqVO) {
        DeviceDO existing = validateDeviceExists(id);
        DeviceStatusRules.Action action = parseAction(reqVO.getAction());
        String targetStatus;
        try {
            if (action == DeviceStatusRules.Action.COMPLETE_REPAIR) {
                targetStatus = reqVO.getTargetStatus() != null
                        ? reqVO.getTargetStatus()
                        : DeviceArchiveStatusEnum.IN_USE;
                DeviceStatusRules.requireCompleteRepair(existing.getStatus(), targetStatus);
            } else {
                DeviceStatusRules.requireTransition(existing.getStatus(), action);
                targetStatus = DeviceStatusRules.targetStatus(action);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw exception(AST_EQUIPMENT_STATUS_INVALID);
        }
        DeviceDO update = new DeviceDO();
        update.setId(id);
        update.setStatus(targetStatus);
        deviceMapper.updateById(update);
        DeviceDO after = deviceMapper.selectById(id);
        appendVersion(id, existing, after,
                DeviceStatusRules.toChangeType(action), reqVO.getChangeDescription());
    }

    @Override
    public PageResult<DeviceDO> getDeviceArchivePage(DeviceArchivePageReqVO pageReqVO) {
        return deviceMapper.selectArchivePage(TenantContextHolder.getRequiredTenantId(), pageReqVO);
    }

    @Override
    public DeviceDO getDeviceArchiveRecord(Long id) {
        return validateDeviceExists(id);
    }

    @Override
    public List<DeviceVersionDO> getDeviceVersionList(Long deviceId) {
        return deviceVersionMapper.selectListByDeviceId(deviceId);
    }

    private DeviceDO validateDeviceExists(Long id) {
        DeviceDO entity = deviceMapper.selectByTenantAndId(TenantContextHolder.getRequiredTenantId(), id);
        if (entity == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        return entity;
    }

    private void validateCustomerAvailable(Long customerId) {
        if (customerId == null) {
            return;
        }
        CustomerSummaryDTO customer = customerQueryApi.getCustomer(customerId);
        if (customer == null || !CustomerLifecycleStatus.ENABLED.name().equals(customer.lifecycleStatus())) {
            throw exception(AST_EQUIPMENT_CUSTOMER_UNAVAILABLE);
        }
    }

    private void validateSnUnique(Long id, String sn) {
        if (sn == null || sn.isEmpty()) {
            return;
        }
        DeviceDO existing = deviceMapper.selectByTenantAndSn(TenantContextHolder.getRequiredTenantId(), sn);
        if (existing != null && (id == null || !Objects.equals(existing.getId(), id))) {
            throw exception(AST_EQUIPMENT_SERIAL_NUMBER_DUPLICATE);
        }
    }

    private DeviceStatusRules.Action parseAction(String action) {
        if (action == null || action.isEmpty()) {
            throw exception(AST_EQUIPMENT_STATUS_INVALID);
        }
        try {
            return DeviceStatusRules.Action.valueOf(action);
        } catch (IllegalArgumentException e) {
            throw exception(AST_EQUIPMENT_STATUS_INVALID);
        }
    }

    /** 追加一条设备版本历史记录（追加只读，仅 INSERT）。 */
    private void appendVersion(Long deviceId, DeviceDO before, DeviceDO after,
                               String changeType, String changeDescription) {
        DeviceVersionDO version = new DeviceVersionDO();
        version.setDeviceId(deviceId);
        version.setVersionNo(deviceVersionMapper.selectMaxVersionNo(deviceId) + 1);
        version.setChangeType(changeType);
        version.setChangeDescription(changeDescription);
        version.setBeforeSnapshot(before == null ? null : JsonUtils.toJsonString(before));
        version.setAfterSnapshot(after == null ? null : JsonUtils.toJsonString(after));
        deviceVersionMapper.insert(version);
    }
}
