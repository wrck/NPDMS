package cn.iocoder.yudao.module.pms.asset.service.equipment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentPageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentStatusChangeReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentVersionMapper;
import cn.iocoder.yudao.module.pms.asset.domain.equipment.EquipmentStatusRules;
import cn.iocoder.yudao.module.pms.asset.enums.EquipmentChangeTypeEnum;
import cn.iocoder.yudao.module.pms.asset.enums.EquipmentStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.*;

/**
 * PMS 设备档案 Service 实现（FR-RES-001 / FR-RES-002）。
 * <p>
 * 序列号全局唯一；状态变更通过 {@link EquipmentStatusRules} 状态机校验；
 * 每次创建/修改/状态变更追加一条 {@code pms_equipment_version} 记录（追加只读）。
 */
@Service
@Validated
@Slf4j
public class EquipmentServiceImpl implements EquipmentService {

    @Resource
    private EquipmentMapper equipmentMapper;
    @Resource
    private EquipmentVersionMapper equipmentVersionMapper;
    @Resource
    private CustomerQueryApi customerQueryApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEquipment(EquipmentSaveReqVO createReqVO) {
        validateCustomerAvailable(createReqVO.getCustomerId());
        validateSerialNumberUnique(null, createReqVO.getSerialNumber());
        EquipmentDO entity = BeanUtils.toBean(createReqVO, EquipmentDO.class);
        entity.setStatus(EquipmentStatusEnum.IN_STOCK);
        equipmentMapper.insert(entity);
        // 追加版本历史：CREATE
        appendVersion(entity.getId(), null, entity, EquipmentChangeTypeEnum.CREATE, "创建设备档案");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEquipment(EquipmentSaveReqVO updateReqVO) {
        EquipmentDO existing = validateEquipmentExists(updateReqVO.getId());
        // 已报废不允许修改
        if (Objects.equals(EquipmentStatusEnum.SCRAPPED, existing.getStatus())) {
            throw exception(AST_EQUIPMENT_SCRAPPED);
        }
        if (!Objects.equals(existing.getCustomerId(), updateReqVO.getCustomerId())) {
            validateCustomerAvailable(updateReqVO.getCustomerId());
        }
        validateSerialNumberUnique(updateReqVO.getId(), updateReqVO.getSerialNumber());
        EquipmentDO update = BeanUtils.toBean(updateReqVO, EquipmentDO.class);
        equipmentMapper.updateById(update);
        // 追加版本历史：UPDATE，after 快照以更新后数据为准
        EquipmentDO after = equipmentMapper.selectById(updateReqVO.getId());
        appendVersion(updateReqVO.getId(), existing, after, EquipmentChangeTypeEnum.UPDATE, "更新设备档案");
    }

    @Override
    public void deleteEquipment(Long id) {
        validateEquipmentExists(id);
        equipmentMapper.deleteById(id);
    }

    @Override
    public EquipmentDO getEquipment(Long id) {
        return equipmentMapper.selectById(id);
    }

    @Override
    public EquipmentDO validateEquipmentExists(Long id) {
        EquipmentDO entity = equipmentMapper.selectById(id);
        if (entity == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<EquipmentDO> getEquipmentPage(EquipmentPageReqVO pageReqVO) {
        return equipmentMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeEquipmentStatus(EquipmentStatusChangeReqVO reqVO) {
        EquipmentDO existing = validateEquipmentExists(reqVO.getId());
        EquipmentStatusRules.Action action = parseAction(reqVO.getAction());
        // 状态机校验 + 计算目标状态
        Integer targetStatus;
        try {
            if (action == EquipmentStatusRules.Action.COMPLETE_REPAIR) {
                // COMPLETE_REPAIR 需显式指定目标状态，默认在用
                targetStatus = reqVO.getTargetStatus() != null
                        ? reqVO.getTargetStatus()
                        : EquipmentStatusEnum.IN_USE;
                EquipmentStatusRules.requireCompleteRepair(existing.getStatus(), targetStatus);
            } else {
                EquipmentStatusRules.requireTransition(existing.getStatus(), action);
                targetStatus = EquipmentStatusRules.targetStatus(action);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw exception(AST_EQUIPMENT_STATUS_INVALID);
        }
        // 更新设备状态
        EquipmentDO update = new EquipmentDO();
        update.setId(reqVO.getId());
        update.setStatus(targetStatus);
        update.setVersion(reqVO.getVersion() != null ? reqVO.getVersion() : existing.getVersion());
        equipmentMapper.updateById(update);
        // 追加版本历史
        EquipmentDO after = equipmentMapper.selectById(reqVO.getId());
        appendVersion(reqVO.getId(), existing, after,
                EquipmentStatusRules.toChangeType(action), reqVO.getChangeDescription());
    }

    @Override
    public List<EquipmentVersionDO> getEquipmentVersionList(Long equipmentId) {
        return equipmentVersionMapper.selectListByEquipmentId(equipmentId);
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

    /**
     * 追加一条设备版本历史记录（追加只读，仅 INSERT）。
     */
    private void appendVersion(Long equipmentId, EquipmentDO before, EquipmentDO after,
                               String changeType, String changeDescription) {
        Integer nextVersionNo = equipmentVersionMapper.selectMaxVersionNo(equipmentId) + 1;
        EquipmentVersionDO version = new EquipmentVersionDO();
        version.setEquipmentId(equipmentId);
        version.setVersionNo(nextVersionNo);
        version.setChangeType(changeType);
        version.setChangeDescription(changeDescription);
        version.setBeforeSnapshot(before == null ? null : JsonUtils.toJsonString(before));
        version.setAfterSnapshot(after == null ? null : JsonUtils.toJsonString(after));
        equipmentVersionMapper.insert(version);
    }

    private void validateSerialNumberUnique(Long id, String serialNumber) {
        if (serialNumber == null || serialNumber.isEmpty()) {
            return;
        }
        EquipmentDO existing = equipmentMapper.selectBySerialNumber(serialNumber);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(AST_EQUIPMENT_SERIAL_NUMBER_DUPLICATE);
        }
    }

    private EquipmentStatusRules.Action parseAction(String action) {
        if (action == null || action.isEmpty()) {
            throw exception(AST_EQUIPMENT_STATUS_INVALID);
        }
        try {
            return EquipmentStatusRules.Action.valueOf(action);
        } catch (IllegalArgumentException e) {
            throw exception(AST_EQUIPMENT_STATUS_INVALID);
        }
    }
}
