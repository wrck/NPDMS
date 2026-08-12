package cn.iocoder.yudao.module.pms.project.service.planchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeApproveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangePhaseSnapshotItem;
import cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo.PlanChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangePhaseSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangeRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.planchange.PlanChangePhaseSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.planchange.PlanChangeRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PLAN_CHANGE_NO_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PLAN_CHANGE_NO_SNAPSHOTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PLAN_CHANGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PLAN_CHANGE_PHASE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PLAN_CHANGE_PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PLAN_CHANGE_STATUS_INVALID;

/**
 * PMS 项目计划变更审批 Service 实现（FR-PROJ-020 / T-V2-PROJ-003）
 * <p>
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已通过 → 4已驳回 → 5已撤回 → 6已终止
 * 通过后生成新基线版本号；applyPlanChange 将快照写入项目阶段并形成新基线。
 */
@Service
@Validated
@Slf4j
public class PlanChangeServiceImpl implements PlanChangeService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_SUBMITTED = 1;
    private static final int STATUS_APPROVING = 2;
    private static final int STATUS_PASSED = 3;
    private static final int STATUS_REJECTED = 4;
    private static final int STATUS_WITHDRAWN = 5;
    private static final int STATUS_TERMINATED = 6;

    @Resource
    private PlanChangeRequestMapper planChangeRequestMapper;
    @Resource
    private PlanChangePhaseSnapshotMapper planChangePhaseSnapshotMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectPhaseMapper projectPhaseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPlanChange(PlanChangeSaveReqVO createReqVO) {
        // 校验单号唯一
        validateChangeNoUnique(null, createReqVO.getChangeNo());
        // 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 校验快照非空且阶段存在
        validateSnapshots(createReqVO.getProjectId(), createReqVO.getPhaseSnapshots());
        // 插入主表
        PlanChangeRequestDO entity = BeanUtils.toBean(createReqVO, PlanChangeRequestDO.class);
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getBaselineVersion() == null) {
            entity.setBaselineVersion(0);
        }
        planChangeRequestMapper.insert(entity);
        // 插入阶段快照
        saveSnapshots(entity.getId(), createReqVO.getPhaseSnapshots());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlanChange(PlanChangeSaveReqVO updateReqVO) {
        PlanChangeRequestDO existing = validateExists(updateReqVO.getId());
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        validateChangeNoUnique(updateReqVO.getId(), updateReqVO.getChangeNo());
        validateProjectExists(updateReqVO.getProjectId());
        validateSnapshots(updateReqVO.getProjectId(), updateReqVO.getPhaseSnapshots());
        // 更新主表（保留状态）
        PlanChangeRequestDO updateObj = BeanUtils.toBean(updateReqVO, PlanChangeRequestDO.class);
        updateObj.setStatus(existing.getStatus());
        planChangeRequestMapper.updateById(updateObj);
        // 重建快照
        planChangePhaseSnapshotMapper.deleteByChangeRequestId(updateReqVO.getId());
        saveSnapshots(updateReqVO.getId(), updateReqVO.getPhaseSnapshots());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlanChange(Long id) {
        PlanChangeRequestDO existing = validateExists(id);
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        planChangePhaseSnapshotMapper.deleteByChangeRequestId(id);
        planChangeRequestMapper.deleteById(id);
    }

    @Override
    public PageResult<PlanChangeRequestDO> getPlanChangePage(PlanChangePageReqVO pageReqVO) {
        return planChangeRequestMapper.selectPage(pageReqVO);
    }

    @Override
    public PlanChangeRequestDO getPlanChange(Long id) {
        return planChangeRequestMapper.selectById(id);
    }

    @Override
    public List<PlanChangePhaseSnapshotDO> getPhaseSnapshots(Long changeRequestId) {
        return planChangePhaseSnapshotMapper.selectListByChangeRequestId(changeRequestId);
    }

    @Override
    public void submitPlanChange(Long id) {
        PlanChangeRequestDO existing = validateExists(id);
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        PlanChangeRequestDO updateObj = new PlanChangeRequestDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_SUBMITTED);
        updateObj.setApplyTime(LocalDateTime.now());
        planChangeRequestMapper.updateById(updateObj);
    }

    @Override
    public void approvePlanChange(PlanChangeApproveReqVO reqVO) {
        PlanChangeRequestDO existing = validateExists(reqVO.getId());
        // 已提交或审批中均可执行审批（支持一次直接审批或退回后再次审批）
        if (!Objects.equals(existing.getStatus(), STATUS_SUBMITTED)
                && !Objects.equals(existing.getStatus(), STATUS_APPROVING)) {
            throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        String action = reqVO.getApproveAction();
        PlanChangeRequestDO updateObj = new PlanChangeRequestDO();
        updateObj.setId(reqVO.getId());
        updateObj.setApproverUserId(reqVO.getApproverUserId());
        updateObj.setApproveTime(LocalDateTime.now());
        updateObj.setApproveOpinion(reqVO.getApproveOpinion());
        updateObj.setApproveAction(action);
        switch (action) {
            case "PASS":
                updateObj.setStatus(STATUS_PASSED);
                // 生成新基线版本号 = 当前基线 + 1
                updateObj.setNewBaselineVersion(
                        (existing.getBaselineVersion() == null ? 0 : existing.getBaselineVersion()) + 1);
                break;
            case "REJECT":
                updateObj.setStatus(STATUS_REJECTED);
                break;
            case "RETURN":
                // 退回到草稿态，便于申请人修改后重新提交
                updateObj.setStatus(STATUS_DRAFT);
                break;
            case "TRANSFER":
            case "COUNTERSIGN":
                // 转办/加签保持审批中状态，仅记录审批动作和意见
                updateObj.setStatus(STATUS_APPROVING);
                break;
            default:
                throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        planChangeRequestMapper.updateById(updateObj);
    }

    @Override
    public void withdrawPlanChange(Long id) {
        PlanChangeRequestDO existing = validateExists(id);
        if (!Objects.equals(existing.getStatus(), STATUS_SUBMITTED)
                && !Objects.equals(existing.getStatus(), STATUS_APPROVING)) {
            throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        PlanChangeRequestDO updateObj = new PlanChangeRequestDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_WITHDRAWN);
        planChangeRequestMapper.updateById(updateObj);
    }

    @Override
    public void terminatePlanChange(Long id) {
        PlanChangeRequestDO existing = validateExists(id);
        if (Objects.equals(existing.getStatus(), STATUS_TERMINATED)) {
            return;
        }
        // 已通过的变更不允许直接终止，需通过反向变更处理
        if (Objects.equals(existing.getStatus(), STATUS_PASSED)) {
            throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        PlanChangeRequestDO updateObj = new PlanChangeRequestDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_TERMINATED);
        planChangeRequestMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyPlanChange(Long id) {
        PlanChangeRequestDO existing = validateExists(id);
        if (!Objects.equals(existing.getStatus(), STATUS_PASSED)) {
            throw exception(PLAN_CHANGE_STATUS_INVALID);
        }
        // 应用快照到项目阶段
        List<PlanChangePhaseSnapshotDO> snapshots = planChangePhaseSnapshotMapper.selectListByChangeRequestId(id);
        if (snapshots == null || snapshots.isEmpty()) {
            throw exception(PLAN_CHANGE_NO_SNAPSHOTS);
        }
        for (PlanChangePhaseSnapshotDO snapshot : snapshots) {
            ProjectPhaseDO phaseUpdate = new ProjectPhaseDO();
            phaseUpdate.setId(snapshot.getPhaseId());
            phaseUpdate.setPlanStartTime(snapshot.getAfterPlanStart());
            phaseUpdate.setPlanEndTime(snapshot.getAfterPlanEnd());
            projectPhaseMapper.updateById(phaseUpdate);
        }
        log.info("[applyPlanChange][变更单 id={} 已应用，共更新 {} 个阶段计划]", id, snapshots.size());
    }

    private PlanChangeRequestDO validateExists(Long id) {
        if (id == null) {
            throw exception(PLAN_CHANGE_NOT_EXISTS);
        }
        PlanChangeRequestDO entity = planChangeRequestMapper.selectById(id);
        if (entity == null) {
            throw exception(PLAN_CHANGE_NOT_EXISTS);
        }
        return entity;
    }

    private void validateChangeNoUnique(Long id, String changeNo) {
        PlanChangeRequestDO existing = planChangeRequestMapper.selectByChangeNo(changeNo);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(PLAN_CHANGE_NO_DUPLICATE);
        }
    }

    private void validateProjectExists(Long projectId) {
        if (projectId == null) {
            throw exception(PLAN_CHANGE_PROJECT_NOT_EXISTS);
        }
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw exception(PLAN_CHANGE_PROJECT_NOT_EXISTS);
        }
    }

    private void validateSnapshots(Long projectId, List<PlanChangePhaseSnapshotItem> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            throw exception(PLAN_CHANGE_NO_SNAPSHOTS);
        }
        // 校验阶段存在且属于当前项目
        Set<Long> phaseIds = new HashSet<>();
        for (PlanChangePhaseSnapshotItem item : snapshots) {
            if (item.getPhaseId() == null) {
                throw exception(PLAN_CHANGE_PHASE_NOT_EXISTS);
            }
            if (!phaseIds.add(item.getPhaseId())) {
                // 同一阶段重复出现，允许覆盖但记录日志
                log.warn("[validateSnapshots][项目 id={} 的阶段 id={} 在快照中重复]", projectId, item.getPhaseId());
            }
        }
        List<ProjectPhaseDO> phases = projectPhaseMapper.selectListByProjectId(projectId);
        Set<Long> existingPhaseIds = new HashSet<>();
        for (ProjectPhaseDO phase : phases) {
            existingPhaseIds.add(phase.getId());
        }
        for (Long phaseId : phaseIds) {
            if (!existingPhaseIds.contains(phaseId)) {
                throw exception(PLAN_CHANGE_PHASE_NOT_EXISTS);
            }
        }
    }

    private void saveSnapshots(Long changeRequestId, List<PlanChangePhaseSnapshotItem> snapshots) {
        List<PlanChangePhaseSnapshotDO> doList = new ArrayList<>(snapshots.size());
        for (PlanChangePhaseSnapshotItem item : snapshots) {
            PlanChangePhaseSnapshotDO snapshot = BeanUtils.toBean(item, PlanChangePhaseSnapshotDO.class);
            snapshot.setId(null); // 强制新建
            snapshot.setChangeRequestId(changeRequestId);
            doList.add(snapshot);
        }
        planChangePhaseSnapshotMapper.insertBatch(doList);
    }

}
