package cn.iocoder.yudao.module.pms.service.service.srvmaintenance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenanceOverrideReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenancePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenanceSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvmaintenance.SrvMaintenanceDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvmaintenance.SrvMaintenanceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_MAINTENANCE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_MAINTENANCE_DATE_INVALID;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_MAINTENANCE_NOT_EXISTS;

/**
 * 维保状态 Service 实现类
 */
@Service
@Validated
public class SrvMaintenanceServiceImpl implements SrvMaintenanceService {

    /**
     * 维保状态：0未生效
     */
    private static final int STATUS_INACTIVE = 0;
    /**
     * 维保状态：1生效中
     */
    private static final int STATUS_ACTIVE = 1;
    /**
     * 维保状态：2即将过期
     */
    private static final int STATUS_EXPIRING_SOON = 2;
    /**
     * 维保状态：3已过期
     */
    private static final int STATUS_EXPIRED = 3;

    /**
     * 即将过期的提前天数阈值
     */
    private static final long EXPIRING_SOON_DAYS = 30L;

    @Resource
    private SrvMaintenanceMapper srvMaintenanceMapper;

    @Override
    public Long createSrvMaintenance(SrvMaintenanceSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getEquipmentId(), createReqVO.getCode());
        validateDateRange(createReqVO.getStartDate(), createReqVO.getEndDate());
        SrvMaintenanceDO maintenance = BeanUtils.toBean(createReqVO, SrvMaintenanceDO.class);
        if (maintenance.getAutoCalculated() == null) {
            maintenance.setAutoCalculated(true);
        }
        if (maintenance.getManualOverride() == null) {
            maintenance.setManualOverride(false);
        }
        // 自动计算模式且未手工覆盖时，初始化状态
        if (Boolean.TRUE.equals(maintenance.getAutoCalculated())
                && !Boolean.TRUE.equals(maintenance.getManualOverride())) {
            maintenance.setMaintenanceStatus(calculateStatusByDate(maintenance.getStartDate(), maintenance.getEndDate()));
        } else if (maintenance.getMaintenanceStatus() == null) {
            maintenance.setMaintenanceStatus(STATUS_INACTIVE);
        }
        srvMaintenanceMapper.insert(maintenance);
        return maintenance.getId();
    }

    @Override
    public void updateSrvMaintenance(SrvMaintenanceSaveReqVO updateReqVO) {
        SrvMaintenanceDO existing = validateSrvMaintenanceExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getEquipmentId(), updateReqVO.getCode());
        validateDateRange(updateReqVO.getStartDate(), updateReqVO.getEndDate());
        SrvMaintenanceDO updateObj = BeanUtils.toBean(updateReqVO, SrvMaintenanceDO.class);
        // 自动计算模式且未手工覆盖时，重新计算状态
        boolean autoCalculated = updateObj.getAutoCalculated() != null
                ? updateObj.getAutoCalculated() : Boolean.TRUE.equals(existing.getAutoCalculated());
        boolean manualOverride = updateObj.getManualOverride() != null
                ? updateObj.getManualOverride() : Boolean.TRUE.equals(existing.getManualOverride());
        if (autoCalculated && !manualOverride) {
            updateObj.setMaintenanceStatus(calculateStatusByDate(updateObj.getStartDate(), updateObj.getEndDate()));
        } else {
            // 保持原状态
            updateObj.setMaintenanceStatus(existing.getMaintenanceStatus());
        }
        srvMaintenanceMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvMaintenance(Long id) {
        validateSrvMaintenanceExists(id);
        srvMaintenanceMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvMaintenanceDO> getSrvMaintenancePage(SrvMaintenancePageReqVO pageReqVO) {
        PageResult<SrvMaintenanceDO> pageResult = srvMaintenanceMapper.selectPage(pageReqVO);
        // 自动计算模式且未手工覆盖的记录，分页查询时同步刷新状态
        pageResult.getList().forEach(this::refreshIfAutoCalculated);
        return pageResult;
    }

    @Override
    public SrvMaintenanceDO getSrvMaintenance(Long id) {
        SrvMaintenanceDO maintenance = srvMaintenanceMapper.selectById(id);
        if (maintenance != null) {
            refreshIfAutoCalculated(maintenance);
        }
        return maintenance;
    }

    @Override
    public List<SrvMaintenanceDO> getSrvMaintenanceListByEquipment(Long equipmentId) {
        if (equipmentId == null) {
            return List.of();
        }
        List<SrvMaintenanceDO> list = srvMaintenanceMapper.selectListByEquipmentId(equipmentId);
        list.forEach(this::refreshIfAutoCalculated);
        return list;
    }

    @Override
    public void calculateStatus(Long id) {
        SrvMaintenanceDO maintenance = validateSrvMaintenanceExists(id);
        int newStatus = calculateStatusByDate(maintenance.getStartDate(), maintenance.getEndDate());
        SrvMaintenanceDO updateObj = new SrvMaintenanceDO();
        updateObj.setId(id);
        updateObj.setMaintenanceStatus(newStatus);
        updateObj.setAutoCalculated(true);
        updateObj.setManualOverride(false);
        updateObj.setOverrideBy(null);
        updateObj.setOverrideTime(null);
        srvMaintenanceMapper.updateById(updateObj);
    }

    @Override
    public void manualOverride(SrvMaintenanceOverrideReqVO reqVO) {
        validateSrvMaintenanceExists(reqVO.getId());
        SrvMaintenanceDO updateObj = new SrvMaintenanceDO();
        updateObj.setId(reqVO.getId());
        updateObj.setMaintenanceStatus(reqVO.getMaintenanceStatus());
        updateObj.setManualOverride(true);
        updateObj.setOverrideTime(LocalDateTime.now());
        srvMaintenanceMapper.updateById(updateObj);
    }

    /**
     * 如果记录处于自动计算模式且未手工覆盖，则按当前日期重新计算并刷新状态。
     *
     * @param maintenance 维保记录
     */
    private void refreshIfAutoCalculated(SrvMaintenanceDO maintenance) {
        if (maintenance == null) {
            return;
        }
        if (!Boolean.TRUE.equals(maintenance.getAutoCalculated())) {
            return;
        }
        if (Boolean.TRUE.equals(maintenance.getManualOverride())) {
            return;
        }
        int newStatus = calculateStatusByDate(maintenance.getStartDate(), maintenance.getEndDate());
        if (!Objects.equals(maintenance.getMaintenanceStatus(), newStatus)) {
            SrvMaintenanceDO updateObj = new SrvMaintenanceDO();
            updateObj.setId(maintenance.getId());
            updateObj.setMaintenanceStatus(newStatus);
            srvMaintenanceMapper.updateById(updateObj);
            maintenance.setMaintenanceStatus(newStatus);
        }
    }

    /**
     * 根据开始日期与结束日期计算维保状态。
     *
     * @param startDate 维保开始日期
     * @param endDate   维保结束日期
     * @return 维保状态
     */
    private int calculateStatusByDate(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        // 缺少日期信息时保持未生效
        if (startDate == null || endDate == null) {
            return STATUS_INACTIVE;
        }
        if (today.isBefore(startDate)) {
            return STATUS_INACTIVE;
        }
        long daysToExpiry = ChronoUnit.DAYS.between(today, endDate);
        if (daysToExpiry < 0) {
            return STATUS_EXPIRED;
        }
        if (daysToExpiry < EXPIRING_SOON_DAYS) {
            return STATUS_EXPIRING_SOON;
        }
        return STATUS_ACTIVE;
    }

    private SrvMaintenanceDO validateSrvMaintenanceExists(Long id) {
        if (id == null) {
            throw exception(SRV_MAINTENANCE_NOT_EXISTS);
        }
        SrvMaintenanceDO maintenance = srvMaintenanceMapper.selectById(id);
        if (maintenance == null) {
            throw exception(SRV_MAINTENANCE_NOT_EXISTS);
        }
        return maintenance;
    }

    private void validateCodeUnique(Long id, Long equipmentId, String code) {
        if (equipmentId == null || code == null) {
            return;
        }
        SrvMaintenanceDO existing = srvMaintenanceMapper.selectByEquipmentIdAndCode(equipmentId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_MAINTENANCE_CODE_DUPLICATE, code);
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw exception(SRV_MAINTENANCE_DATE_INVALID);
        }
    }

}
