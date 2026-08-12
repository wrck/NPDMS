package cn.iocoder.yudao.module.pms.project.service.projectclosure;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosurePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosureSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.ProjectClosureMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_VALIDATION_FAILED;

/**
 * 项目闭环审批 Service 实现类
 * <p>
 * 状态机：0草稿 → 1待审批 → 2审批中 → 3已通过 → 4已驳回 → 5已归档
 * 门禁：闭环通过（pass 2→3）前校验 阶段完成 + 验收通过 + 问题关闭 + 审批完成
 * 【待确认：遗留问题闭环规则】允许带条件移交（CONDITIONAL），具体移交条件由业务规则补充，本实现承载流程数据。
 * <p>
 * 注：问题关闭、审批完成校验涉及跨模块（巡检问题 pms-module-service、BPM 审批），
 * 受模块间领域边界约束不在本模块直接访问，留作占位由集成层补充。
 */
@Service
@Validated
public class ProjectClosureServiceImpl implements ProjectClosureService {

    /**
     * 状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 状态：1待审批
     */
    private static final int STATUS_PENDING_APPROVE = 1;
    /**
     * 状态：2审批中
     */
    private static final int STATUS_APPROVING = 2;
    /**
     * 状态：3已通过
     */
    private static final int STATUS_PASSED = 3;
    /**
     * 状态：4已驳回
     */
    private static final int STATUS_REJECTED = 4;
    /**
     * 状态：5已归档
     */
    private static final int STATUS_ARCHIVED = 5;

    /**
     * 项目阶段状态：2已完成
     */
    private static final int PHASE_STATUS_COMPLETED = 2;
    /**
     * 项目阶段状态：3已跳过
     */
    private static final int PHASE_STATUS_SKIPPED = 3;
    /**
     * 验收类型：终验
     */
    private static final String ACCEPTANCE_TYPE_FINAL = "FINAL";
    /**
     * 验收状态：已通过
     */
    private static final int ACCEPTANCE_STATUS_PASSED = 3;
    /**
     * 验收状态：已归档
     */
    private static final int ACCEPTANCE_STATUS_ARCHIVED = 5;
    /**
     * 闭环类型：带条件移交
     */
    private static final String CLOSURE_TYPE_CONDITIONAL = "CONDITIONAL";

    @Resource
    private ProjectClosureMapper projectClosureMapper;
    @Resource
    private ProjectPhaseMapper projectPhaseMapper;
    @Resource
    private AcceptanceMapper acceptanceMapper;

    @Override
    public Long createProjectClosure(ProjectClosureSaveReqVO createReqVO) {
        // 校验项目内编码唯一
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 插入
        ProjectClosureDO entity = BeanUtils.toBean(createReqVO, ProjectClosureDO.class);
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getClosureType() == null) {
            entity.setClosureType("NORMAL");
        }
        projectClosureMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateProjectClosure(ProjectClosureSaveReqVO updateReqVO) {
        ProjectClosureDO existing = validateExists(updateReqVO.getId());
        // 校验项目内编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 仅草稿态允许修改核心字段
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_PROJECT_CLOSURE_STATUS_INVALID);
        }
        ProjectClosureDO updateObj = BeanUtils.toBean(updateReqVO, ProjectClosureDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        projectClosureMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectClosure(Long id) {
        ProjectClosureDO existing = validateExists(id);
        // 仅草稿或已驳回状态允许删除
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(ACC_PROJECT_CLOSURE_STATUS_INVALID);
        }
        projectClosureMapper.deleteById(id);
    }

    @Override
    public PageResult<ProjectClosureDO> getProjectClosurePage(ProjectClosurePageReqVO pageReqVO) {
        return projectClosureMapper.selectPage(pageReqVO);
    }

    @Override
    public ProjectClosureDO getProjectClosure(Long id) {
        return projectClosureMapper.selectById(id);
    }

    @Override
    public void submitProjectClosure(Long id) {
        ProjectClosureDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_PROJECT_CLOSURE_STATUS_INVALID);
        }
        ProjectClosureDO updateObj = new ProjectClosureDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PENDING_APPROVE);
        updateObj.setApplyTime(LocalDateTime.now());
        projectClosureMapper.updateById(updateObj);
    }

    @Override
    public void startApprove(Long id) {
        ProjectClosureDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_APPROVE)) {
            throw exception(ACC_PROJECT_CLOSURE_STATUS_INVALID);
        }
        updateStatus(id, STATUS_APPROVING);
    }

    @Override
    public void passProjectClosure(Long id) {
        ProjectClosureDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_APPROVING)) {
            throw exception(ACC_PROJECT_CLOSURE_STATUS_INVALID);
        }
        // 闭环门禁校验：阶段完成 + 验收通过（问题关闭、审批完成留作占位）
        validateClosureReadiness(entity);
        ProjectClosureDO updateObj = new ProjectClosureDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PASSED);
        updateObj.setApproveTime(LocalDateTime.now());
        projectClosureMapper.updateById(updateObj);
    }

    @Override
    public void rejectProjectClosure(Long id) {
        ProjectClosureDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_APPROVING)) {
            throw exception(ACC_PROJECT_CLOSURE_STATUS_INVALID);
        }
        ProjectClosureDO updateObj = new ProjectClosureDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_REJECTED);
        updateObj.setApproveTime(LocalDateTime.now());
        projectClosureMapper.updateById(updateObj);
    }

    @Override
    public void archiveProjectClosure(Long id) {
        ProjectClosureDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PASSED)) {
            throw exception(ACC_PROJECT_CLOSURE_STATUS_INVALID);
        }
        ProjectClosureDO updateObj = new ProjectClosureDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_ARCHIVED);
        updateObj.setArchiveTime(LocalDateTime.now());
        projectClosureMapper.updateById(updateObj);
    }

    /**
     * 闭环就绪校验：阶段完成 + 验收通过
     * 【待确认：遗留问题闭环规则】带条件移交（CONDITIONAL）可放行部分校验，具体规则由业务补充。
     * 问题关闭、审批完成涉及跨模块，受领域边界约束留作占位。
     */
    private void validateClosureReadiness(ProjectClosureDO entity) {
        // 1. 阶段完成：项目所有阶段必须为已完成或已跳过
        List<ProjectPhaseDO> phases = projectPhaseMapper.selectListByProjectId(entity.getProjectId());
        if (phases != null && !phases.isEmpty()) {
            for (ProjectPhaseDO phase : phases) {
                if (!Objects.equals(phase.getStatus(), PHASE_STATUS_COMPLETED)
                        && !Objects.equals(phase.getStatus(), PHASE_STATUS_SKIPPED)) {
                    throw exception(ACC_PROJECT_CLOSURE_VALIDATION_FAILED,
                            "阶段[" + phase.getName() + "]尚未完成");
                }
            }
        }
        // 2. 验收通过：项目内须存在终验且状态为已通过或已归档
        List<AcceptanceDO> finalAcceptances = acceptanceMapper.selectList(new LambdaQueryWrapperX<AcceptanceDO>()
                .eq(AcceptanceDO::getProjectId, entity.getProjectId())
                .eq(AcceptanceDO::getAcceptanceType, ACCEPTANCE_TYPE_FINAL)
                .in(AcceptanceDO::getStatus, Arrays.asList(ACCEPTANCE_STATUS_PASSED, ACCEPTANCE_STATUS_ARCHIVED)));
        if (finalAcceptances == null || finalAcceptances.isEmpty()) {
            throw exception(ACC_PROJECT_CLOSURE_VALIDATION_FAILED, "终验尚未通过");
        }
        // 3. 问题关闭、4. 审批完成：跨模块（巡检问题 pms-module-service、BPM），受领域边界约束留作占位
        // 由集成层补充实际校验规则
    }

    private void updateStatus(Long id, int status) {
        ProjectClosureDO updateObj = new ProjectClosureDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        projectClosureMapper.updateById(updateObj);
    }

    private ProjectClosureDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_PROJECT_CLOSURE_NOT_EXISTS);
        }
        ProjectClosureDO entity = projectClosureMapper.selectById(id);
        if (entity == null) {
            throw exception(ACC_PROJECT_CLOSURE_NOT_EXISTS);
        }
        return entity;
    }

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (projectId == null || code == null) {
            return;
        }
        ProjectClosureDO existing = projectClosureMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(ACC_PROJECT_CLOSURE_CODE_DUPLICATE, code);
        }
    }

}
