package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceApproveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernancePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectGovernanceActionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterLegacyConvert;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectGovernanceActionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.GOVERNANCE_ACTION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.GOVERNANCE_ACTION_NO_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.GOVERNANCE_ACTION_PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.GOVERNANCE_ACTION_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.GOVERNANCE_ACTION_TYPE_INVALID;

/**
 * PMS 项目治理动作 Service 实现（FR-PROJ-022 / T-V2-PROJ-003）
 * <p>
 * 动作类型：ROLLBACK 回退总部重新指派 / DIRECT_CLOSE 直接关闭
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已执行 → 4已驳回 → 5已撤回
 * <p>
 * 项目状态约定：0立项待指派 / 1进行中 / 2已完成 / 3已关闭
 * - ROLLBACK：执行时将项目状态置回 0（待指派），清空项目经理
 * - DIRECT_CLOSE：执行时将项目状态置为 3（已关闭）
 * <p>
 * 项目主档操作已迁新权威主档 {@code proj_project}（AI-MIG-000 口径A 有承接领域 /
 * V260 已全量前向导入）；粗化状态按 V260 契约双向映射（0->S0 / 3->S6，逆向终态
 * 还原 S6->3）。动作审计字段（before/after Integer 粗化状态）沿用旧口径。
 */
@Service
@Validated
@Slf4j
public class ProjectGovernanceServiceImpl implements ProjectGovernanceService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_SUBMITTED = 1;
    private static final int STATUS_APPROVING = 2;
    private static final int STATUS_EXECUTED = 3;
    private static final int STATUS_REJECTED = 4;
    private static final int STATUS_WITHDRAWN = 5;

    /** 动作类型：回退总部 */
    private static final String ACTION_TYPE_ROLLBACK = "ROLLBACK";
    /** 动作类型：直接关闭 */
    private static final String ACTION_TYPE_DIRECT_CLOSE = "DIRECT_CLOSE";

    /** 项目状态：立项待指派 */
    private static final int PROJECT_STATUS_PENDING_ASSIGN = 0;
    /** 项目状态：已关闭 */
    private static final int PROJECT_STATUS_CLOSED = 3;

    @Resource
    private ProjectGovernanceActionMapper projectGovernanceActionMapper;
    @Resource(name = "projectMasterMapper")
    private ProjectMasterMapper projectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGovernanceAction(ProjectGovernanceSaveReqVO createReqVO) {
        validateActionNoUnique(null, createReqVO.getActionNo());
        validateProjectExists(createReqVO.getProjectId());
        validateActionType(createReqVO.getActionType());
        ProjectGovernanceActionDO entity = BeanUtils.toBean(createReqVO, ProjectGovernanceActionDO.class);
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        projectGovernanceActionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateGovernanceAction(ProjectGovernanceSaveReqVO updateReqVO) {
        ProjectGovernanceActionDO existing = validateExists(updateReqVO.getId());
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(GOVERNANCE_ACTION_STATUS_INVALID);
        }
        validateActionNoUnique(updateReqVO.getId(), updateReqVO.getActionNo());
        validateProjectExists(updateReqVO.getProjectId());
        validateActionType(updateReqVO.getActionType());
        ProjectGovernanceActionDO updateObj = BeanUtils.toBean(updateReqVO, ProjectGovernanceActionDO.class);
        updateObj.setStatus(existing.getStatus());
        projectGovernanceActionMapper.updateById(updateObj);
    }

    @Override
    public void deleteGovernanceAction(Long id) {
        ProjectGovernanceActionDO existing = validateExists(id);
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(GOVERNANCE_ACTION_STATUS_INVALID);
        }
        projectGovernanceActionMapper.deleteById(id);
    }

    @Override
    public PageResult<ProjectGovernanceActionDO> getGovernanceActionPage(ProjectGovernancePageReqVO pageReqVO) {
        return projectGovernanceActionMapper.selectPage(pageReqVO);
    }

    @Override
    public ProjectGovernanceActionDO getGovernanceAction(Long id) {
        return projectGovernanceActionMapper.selectById(id);
    }

    @Override
    public void submitGovernanceAction(Long id) {
        ProjectGovernanceActionDO existing = validateExists(id);
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(GOVERNANCE_ACTION_STATUS_INVALID);
        }
        ProjectGovernanceActionDO updateObj = new ProjectGovernanceActionDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_SUBMITTED);
        updateObj.setApplyTime(LocalDateTime.now());
        projectGovernanceActionMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveGovernanceAction(ProjectGovernanceApproveReqVO reqVO) {
        ProjectGovernanceActionDO existing = validateExists(reqVO.getId());
        if (!Objects.equals(existing.getStatus(), STATUS_SUBMITTED)
                && !Objects.equals(existing.getStatus(), STATUS_APPROVING)) {
            throw exception(GOVERNANCE_ACTION_STATUS_INVALID);
        }
        String action = reqVO.getApproveAction();
        ProjectGovernanceActionDO updateObj = new ProjectGovernanceActionDO();
        updateObj.setId(reqVO.getId());
        updateObj.setApproverUserId(reqVO.getApproverUserId());
        updateObj.setApproveTime(LocalDateTime.now());
        updateObj.setApproveOpinion(reqVO.getApproveOpinion());
        switch (action) {
            case "PASS":
                // 通过并执行
                executeGovernanceAction(existing, updateObj);
                updateObj.setStatus(STATUS_EXECUTED);
                updateObj.setExecuteTime(LocalDateTime.now());
                break;
            case "REJECT":
                updateObj.setStatus(STATUS_REJECTED);
                break;
            case "RETURN":
                updateObj.setStatus(STATUS_DRAFT);
                break;
            default:
                throw exception(GOVERNANCE_ACTION_STATUS_INVALID);
        }
        projectGovernanceActionMapper.updateById(updateObj);
    }

    @Override
    public void withdrawGovernanceAction(Long id) {
        ProjectGovernanceActionDO existing = validateExists(id);
        if (!Objects.equals(existing.getStatus(), STATUS_SUBMITTED)
                && !Objects.equals(existing.getStatus(), STATUS_APPROVING)) {
            throw exception(GOVERNANCE_ACTION_STATUS_INVALID);
        }
        ProjectGovernanceActionDO updateObj = new ProjectGovernanceActionDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_WITHDRAWN);
        projectGovernanceActionMapper.updateById(updateObj);
    }

    /**
     * 执行治理动作：回退或关闭项目
     * - ROLLBACK：项目状态置回 0（待指派），清空项目经理
     * - DIRECT_CLOSE：项目状态置为 3（已关闭）
     */
    private void executeGovernanceAction(ProjectGovernanceActionDO existing, ProjectGovernanceActionDO updateObj) {
        ProjectMasterDO project = projectMapper.selectById(existing.getProjectId());
        if (project == null) {
            throw exception(GOVERNANCE_ACTION_PROJECT_NOT_EXISTS);
        }
        // 记录执行前状态（动作审计沿用旧 Integer 粗化口径，逆向终态还原见转换器）
        updateObj.setBeforeProjectStatus(
                ProjectMasterLegacyConvert.toLegacyStatus(project.getStatus()));
        updateObj.setBeforeManagerUserId(project.getManagerId());

        ProjectMasterDO projectUpdate = new ProjectMasterDO();
        projectUpdate.setId(project.getId());
        if (ACTION_TYPE_ROLLBACK.equals(existing.getActionType())) {
            // 回退：状态置为待指派（S0），清空项目经理
            projectUpdate.setStatus(
                    ProjectMasterLegacyConvert.toMasterStatus(PROJECT_STATUS_PENDING_ASSIGN));
            projectUpdate.setManagerId(null);
            updateObj.setAfterProjectStatus(PROJECT_STATUS_PENDING_ASSIGN);
            updateObj.setAfterManagerUserId(null);
            log.info("[executeGovernanceAction][项目 id={} 回退总部，原状态={} 原经理={}]",
                    project.getId(), project.getStatus(), project.getManagerId());
        } else if (ACTION_TYPE_DIRECT_CLOSE.equals(existing.getActionType())) {
            // 关闭：状态置为已关闭（S6）
            projectUpdate.setStatus(
                    ProjectMasterLegacyConvert.toMasterStatus(PROJECT_STATUS_CLOSED));
            updateObj.setAfterProjectStatus(PROJECT_STATUS_CLOSED);
            updateObj.setAfterManagerUserId(project.getManagerId());
            log.info("[executeGovernanceAction][项目 id={} 直接关闭，原状态={}]", project.getId(), project.getStatus());
        } else {
            throw exception(GOVERNANCE_ACTION_TYPE_INVALID);
        }
        projectMapper.updateById(projectUpdate);
    }

    private ProjectGovernanceActionDO validateExists(Long id) {
        if (id == null) {
            throw exception(GOVERNANCE_ACTION_NOT_EXISTS);
        }
        ProjectGovernanceActionDO entity = projectGovernanceActionMapper.selectById(id);
        if (entity == null) {
            throw exception(GOVERNANCE_ACTION_NOT_EXISTS);
        }
        return entity;
    }

    private void validateActionNoUnique(Long id, String actionNo) {
        ProjectGovernanceActionDO existing = projectGovernanceActionMapper.selectByActionNo(actionNo);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(GOVERNANCE_ACTION_NO_DUPLICATE);
        }
    }

    private void validateProjectExists(Long projectId) {
        if (projectId == null) {
            throw exception(GOVERNANCE_ACTION_PROJECT_NOT_EXISTS);
        }
        if (projectMapper.selectById(projectId) == null) {
            throw exception(GOVERNANCE_ACTION_PROJECT_NOT_EXISTS);
        }
    }

    private void validateActionType(String actionType) {
        if (!ACTION_TYPE_ROLLBACK.equals(actionType) && !ACTION_TYPE_DIRECT_CLOSE.equals(actionType)) {
            throw exception(GOVERNANCE_ACTION_TYPE_INVALID);
        }
    }

}
