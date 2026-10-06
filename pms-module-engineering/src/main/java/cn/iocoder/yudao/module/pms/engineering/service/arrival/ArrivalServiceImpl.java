package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalEditableDeleteQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 到货签收 Service 实现（FR-ENG-021）。
 * <p>
 * 状态流转：0 待签收 → 1 已签收 / 2 异常。
 */
@Service
@Validated
public class ArrivalServiceImpl implements ArrivalService {

    @Resource
    private cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi deviceSelectionApi;

    @Resource
    private ArrivalMapper arrivalMapper;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;
    @Resource private ArrivalDeliveryRegistration deliveryRegistration;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArrival(ArrivalSaveReqVO createReqVO) {
        if (createReqVO.getEquipmentId() != null) {
            deviceSelectionApi.validateSelection(createReqVO.getProjectId(), java.util.List.of(createReqVO.getEquipmentId()));
        }
        ArrivalDO arrival = BeanUtils.toBean(createReqVO, ArrivalDO.class);
        arrival.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.ARRIVAL, arrivalMapper));
        arrival.setStatus(0); // Only the sign command produces a signed record.
        arrival.setVersion(0L);
        if (arrival.getQuantity() == null) {
            arrival.setQuantity(1);
        }
        arrivalMapper.insert(arrival);
        return arrival.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArrival(ArrivalSaveReqVO updateReqVO) {
        ArrivalDO existing = validateArrivalExists(updateReqVO.getId());
        deliveryRegistration.requireWrite(existing);
        deliveryRegistration.preventSourceMove(existing,updateReqVO.getProjectId());
        Long equipmentId = updateReqVO.getEquipmentId() != null ? updateReqVO.getEquipmentId() : existing.getEquipmentId();
        if (equipmentId != null) {
            deviceSelectionApi.validateSelection(updateReqVO.getProjectId(), java.util.List.of(equipmentId));
        }
        validateStatus(existing, 0, 2);
        validateVersion(existing, updateReqVO.getVersion());
        ArrivalDO update = BeanUtils.toBean(updateReqVO, ArrivalDO.class);
        update.setStatus(existing.getStatus());
        update.setVersion(existing.getVersion());
        update.setTenantId(existing.getTenantId());
        deliveryRegistration.requireWrite(update);
        updateRecord(update);
        deliveryRegistration.registerFiles(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArrival(Long id) {
        ArrivalDO existing = validateArrivalExists(id);
        deliveryRegistration.requireDelete(existing);
        validateStatus(existing, 0, 2);
        if (arrivalMapper.deleteEditable(new ArrivalEditableDeleteQuery(id, existing.getVersion())) != 1) {
            throw exception(ARRIVAL_VERSION_NOT_MATCH);
        }
    }

    @Override
    public ArrivalDO getArrival(Long id) {
        return arrivalMapper.selectById(id);
    }

    @Override
    public PageResult<ArrivalDO> getArrivalPage(ArrivalPageReqVO pageReqVO) {
        return arrivalMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signArrival(Long id) {
        ArrivalDO arrival = validateArrivalExists(id);
        deliveryRegistration.requireWrite(arrival);
        validateStatus(arrival, 0); // 待签收 → 已签收
        deliveryRegistration.registerFiles(arrival);
        updateStatus(arrival, 1);
        deliveryRegistration.registerSigned(arrival);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAbnormal(Long id) {
        ArrivalDO arrival = validateArrivalExists(id);
        deliveryRegistration.requireWrite(arrival);
        validateStatus(arrival, 0); // 待签收 → 异常
        updateStatus(arrival, 2);
    }

    // ==================== 内部工具方法 ====================

    private ArrivalDO validateArrivalExists(Long id) {
        ArrivalDO arrival = arrivalMapper.selectDeliveryOwnerForUpdate(new cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalDeliveryOwnerQuery(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(),id));
        if (arrival == null) {
            throw exception(ARRIVAL_NOT_EXISTS);
        }
        return arrival;
    }

    private void validateVersion(ArrivalDO arrival, Integer version) {
        if (version == null || !Objects.equals(arrival.getVersion(), version.longValue())) {
            throw exception(ARRIVAL_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(ArrivalDO arrival, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(arrival.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(ARRIVAL_STATUS_INVALID);
    }

    private void updateStatus(ArrivalDO arrival, int newStatus) {
        arrival.setStatus(newStatus);
        updateRecord(arrival);
    }

    private void updateRecord(ArrivalDO arrival) {
        // Keep the loaded version for the optimistic-lock predicate. MyBatis-Plus
        // owns the increment; reject an update that lost a concurrent race.
        if (arrivalMapper.updateById(arrival) != 1) {
            throw exception(ARRIVAL_VERSION_NOT_MATCH);
        }
    }
}
