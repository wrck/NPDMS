package cn.iocoder.yudao.module.pms.project.service.maintenancetransition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.maintenancetransition.MaintenanceTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.maintenancetransition.MaintenanceTransitionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_MAINTENANCE_TRANSITION_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_MAINTENANCE_TRANSITION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_MAINTENANCE_TRANSITION_STATUS_INVALID;

/**
 * 转维保 Service 实现类
 * <p>
 * 状态机：0草稿 → 1待生效 → 2生效中 → 3已过期 → 4已续保
 * 维护期：基于验收时间和维保年限自动生成设备维护期（activate 时若起止日期为空则自动计算）
 */
@Service
@Validated
public class MaintenanceTransitionServiceImpl implements MaintenanceTransitionService {

    /**
     * 状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 状态：1待生效
     */
    private static final int STATUS_PENDING_ACTIVE = 1;
    /**
     * 状态：2生效中
     */
    private static final int STATUS_ACTIVE = 2;
    /**
     * 状态：3已过期
     */
    private static final int STATUS_EXPIRED = 3;
    /**
     * 状态：4已续保
     */
    private static final int STATUS_RENEWED = 4;

    @Resource
    private MaintenanceTransitionMapper maintenanceTransitionMapper;
    @Resource
    private AcceptanceMapper acceptanceMapper;

    @Override
    public Long createMaintenanceTransition(MaintenanceTransitionSaveReqVO createReqVO) {
        // 校验项目内编码唯一
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 插入
        MaintenanceTransitionDO entity = BeanUtils.toBean(createReqVO, MaintenanceTransitionDO.class);
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        maintenanceTransitionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateMaintenanceTransition(MaintenanceTransitionSaveReqVO updateReqVO) {
        MaintenanceTransitionDO existing = validateExists(updateReqVO.getId());
        // 校验项目内编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 仅草稿态允许修改核心字段
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_MAINTENANCE_TRANSITION_STATUS_INVALID);
        }
        MaintenanceTransitionDO updateObj = BeanUtils.toBean(updateReqVO, MaintenanceTransitionDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        maintenanceTransitionMapper.updateById(updateObj);
    }

    @Override
    public void deleteMaintenanceTransition(Long id) {
        MaintenanceTransitionDO existing = validateExists(id);
        // 仅草稿状态允许删除
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_MAINTENANCE_TRANSITION_STATUS_INVALID);
        }
        maintenanceTransitionMapper.deleteById(id);
    }

    @Override
    public PageResult<MaintenanceTransitionDO> getMaintenanceTransitionPage(MaintenanceTransitionPageReqVO pageReqVO) {
        return maintenanceTransitionMapper.selectPage(pageReqVO);
    }

    @Override
    public MaintenanceTransitionDO getMaintenanceTransition(Long id) {
        return maintenanceTransitionMapper.selectById(id);
    }

    @Override
    public void submitMaintenanceTransition(Long id) {
        MaintenanceTransitionDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_MAINTENANCE_TRANSITION_STATUS_INVALID);
        }
        updateStatus(id, STATUS_PENDING_ACTIVE);
    }

    @Override
    public void activate(Long id) {
        MaintenanceTransitionDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_ACTIVE)) {
            throw exception(ACC_MAINTENANCE_TRANSITION_STATUS_INVALID);
        }
        // 基于验收时间和维保年限自动生成设备维护期
        MaintenanceTransitionDO updateObj = new MaintenanceTransitionDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_ACTIVE);
        updateObj.setActivateTime(LocalDateTime.now());
        // 若起止日期未设置，则根据验收时间和维保年限自动计算
        LocalDate startDate = entity.getStartDate();
        if (startDate == null) {
            startDate = resolveStartDateFromAcceptance(entity.getAcceptanceId());
        }
        if (startDate != null) {
            updateObj.setStartDate(startDate);
            Integer years = entity.getMaintenanceYears();
            if (years != null && years > 0 && entity.getEndDate() == null) {
                updateObj.setEndDate(startDate.plusYears(years));
            }
        }
        maintenanceTransitionMapper.updateById(updateObj);
    }

    @Override
    public void expire(Long id) {
        MaintenanceTransitionDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_ACTIVE)) {
            throw exception(ACC_MAINTENANCE_TRANSITION_STATUS_INVALID);
        }
        MaintenanceTransitionDO updateObj = new MaintenanceTransitionDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_EXPIRED);
        updateObj.setExpireTime(LocalDateTime.now());
        maintenanceTransitionMapper.updateById(updateObj);
    }

    @Override
    public void renew(Long id) {
        MaintenanceTransitionDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_EXPIRED)) {
            throw exception(ACC_MAINTENANCE_TRANSITION_STATUS_INVALID);
        }
        MaintenanceTransitionDO updateObj = new MaintenanceTransitionDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_RENEWED);
        // 续保结束日期：若未设置但有续保年限，则基于原维保结束日期计算
        if (entity.getRenewEndDate() == null && entity.getRenewYears() != null && entity.getRenewYears() > 0) {
            LocalDate baseDate = entity.getEndDate() != null ? entity.getEndDate() : LocalDate.now();
            updateObj.setRenewEndDate(baseDate.plusYears(entity.getRenewYears()));
        }
        maintenanceTransitionMapper.updateById(updateObj);
    }

    /**
     * 从关联验收记录解析维保起始日期（验收日期）
     */
    private LocalDate resolveStartDateFromAcceptance(Long acceptanceId) {
        if (acceptanceId == null) {
            return null;
        }
        AcceptanceDO acceptance = acceptanceMapper.selectById(acceptanceId);
        if (acceptance == null) {
            return null;
        }
        return acceptance.getAcceptanceDate();
    }

    private void updateStatus(Long id, int status) {
        MaintenanceTransitionDO updateObj = new MaintenanceTransitionDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        maintenanceTransitionMapper.updateById(updateObj);
    }

    private MaintenanceTransitionDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_MAINTENANCE_TRANSITION_NOT_EXISTS);
        }
        MaintenanceTransitionDO entity = maintenanceTransitionMapper.selectById(id);
        if (entity == null) {
            throw exception(ACC_MAINTENANCE_TRANSITION_NOT_EXISTS);
        }
        return entity;
    }

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (projectId == null || code == null) {
            return;
        }
        MaintenanceTransitionDO existing = maintenanceTransitionMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(ACC_MAINTENANCE_TRANSITION_CODE_DUPLICATE, code);
        }
    }

}
