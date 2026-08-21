package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectCreateFromTemplateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask.ProjectTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectteam.ProjectTeamMemberMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_SOURCE_KEY_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_IN_USE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_ENABLED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_SNAPSHOT_INVALID;

/**
 * PMS 项目模板 Service 实现类
 */
@Service
@Validated
public class ProjectTemplateServiceImpl implements ProjectTemplateService {

    @Resource
    private ProjectTemplateMapper projectTemplateMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private CustomerMapper customerMapper;
    @Resource
    private ProjectPhaseMapper projectPhaseMapper;
    @Resource
    private ProjectTaskMapper projectTaskMapper;
    @Resource
    private ProjectTeamMemberMapper projectTeamMemberMapper;

    @Override
    public Long createProjectTemplate(ProjectTemplateSaveReqVO reqVO) {
        // 校验编码唯一
        validateCodeUnique(null, reqVO.getCode());
        ProjectTemplateDO template = BeanUtils.toBean(reqVO, ProjectTemplateDO.class);
        if (template.getStatus() == null) {
            template.setStatus("DRAFT");
        }
        if (template.getSort() == null) {
            template.setSort(0);
        }
        projectTemplateMapper.insert(template);
        return template.getId();
    }

    @Override
    public void updateProjectTemplate(ProjectTemplateSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateCodeUnique(reqVO.getId(), reqVO.getCode());
        ProjectTemplateDO updateObj = BeanUtils.toBean(reqVO, ProjectTemplateDO.class);
        projectTemplateMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectTemplate(Long id) {
        validateExists(id);
        // 校验未被项目引用
        Long projectCount = projectMapper.selectCount(ProjectDO::getTemplateId, id);
        if (projectCount != null && projectCount > 0) {
            throw exception(PROJECT_TEMPLATE_IN_USE);
        }
        projectTemplateMapper.deleteById(id);
    }

    @Override
    public ProjectTemplateDO getProjectTemplate(Long id) {
        return projectTemplateMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO reqVO) {
        return projectTemplateMapper.selectPage(reqVO);
    }

    @Override
    public List<ProjectTemplateDO> getEnabledProjectTemplateList() {
        return projectTemplateMapper.selectEnabledList();
    }

    @Override
    public List<ProjectTemplateDO> getEnabledProjectTemplateListByType(String projectType) {
        return projectTemplateMapper.selectEnabledListByType(projectType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProjectFromTemplate(ProjectCreateFromTemplateReqVO reqVO) {
        // 1. 校验模板存在且启用
        ProjectTemplateDO template = projectTemplateMapper.selectById(reqVO.getTemplateId());
        if (template == null) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
        if (!"PUBLISHED".equals(template.getStatus())) {
            throw exception(PROJECT_TEMPLATE_NOT_ENABLED);
        }
        // 2. 校验项目编码唯一、来源业务键唯一、客户存在
        if (projectMapper.selectByCode(reqVO.getCode()) != null) {
            throw exception(PROJECT_CODE_DUPLICATE);
        }
        if (projectMapper.selectBySourceSystemAndBusinessKey(
                reqVO.getSourceSystem(), reqVO.getSourceBusinessKey()) != null) {
            throw exception(PROJECT_SOURCE_KEY_DUPLICATE);
        }
        CustomerDO customer = customerMapper.selectById(reqVO.getCustomerId());
        if (customer == null) {
            throw exception(PROJECT_CUSTOMER_NOT_EXISTS);
        }

        // 3. 校验快照
        TemplateSnapshot snapshot = template.getSnapshotJson();
        validateSnapshot(snapshot);

        // 4. 创建项目主记录
        ProjectDO project = new ProjectDO();
        project.setCode(reqVO.getCode());
        project.setName(reqVO.getName());
        project.setCustomerId(reqVO.getCustomerId());
        project.setContractCode(reqVO.getContractCode());
        project.setProjectType(template.getProjectType());
        project.setSourceSystem(reqVO.getSourceSystem());
        project.setSourceBusinessKey(reqVO.getSourceBusinessKey());
        project.setStatus(0); // 待指派
        project.setTemplateId(template.getId());
        project.setManagerUserId(reqVO.getManagerUserId());
        project.setParentId(null);
        project.setDepth(0);
        project.setSort(0);
        projectMapper.insert(project);
        // 回填树字段
        ProjectDO treeUpdate = new ProjectDO();
        treeUpdate.setId(project.getId());
        treeUpdate.setRootId(project.getId());
        treeUpdate.setPath("/" + project.getId() + "/");
        projectMapper.updateById(treeUpdate);

        // 5. 批量创建阶段
        Map<String, Long> phaseCodeToIdMap = new LinkedHashMap<>();
        if (snapshot.getPhases() != null) {
            for (TemplateSnapshot.PhaseDef phaseDef : snapshot.getPhases()) {
                ProjectPhaseDO phase = new ProjectPhaseDO();
                phase.setProjectId(project.getId());
                phase.setTemplateId(null);
                phase.setName(phaseDef.getPhaseName());
                phase.setCode(phaseDef.getPhaseCode());
                phase.setSort(phaseDef.getSortOrder() != null ? phaseDef.getSortOrder() : 0);
                phase.setStatus(0); // 未开始
                phase.setEntryCriteria(phaseDef.getEntryCriteria());
                phase.setExitCriteria(phaseDef.getExitCriteria());
                projectPhaseMapper.insert(phase);
                phaseCodeToIdMap.put(phaseDef.getPhaseCode(), phase.getId());
            }
        }

        // 6. 批量创建任务（两阶段：先插入，再回填 parent/root/path/depth）
        Map<String, Long> taskCodeToIdMap = new LinkedHashMap<>();
        if (snapshot.getTasks() != null) {
            // 6.1 第一遍：插入全部任务
            for (TemplateSnapshot.TaskDef taskDef : snapshot.getTasks()) {
                ProjectTaskDO task = new ProjectTaskDO();
                task.setProjectId(project.getId());
                task.setName(taskDef.getTaskName());
                task.setCode(taskDef.getTaskCode());
                task.setDescription(taskDef.getDescription());
                task.setPriority(taskDef.getPriority());
                task.setSort(taskDef.getSortOrder() != null ? taskDef.getSortOrder() : 0);
                task.setEstimatedHours(taskDef.getEstimatedHours());
                task.setStatus(0); // 草稿
                task.setParentId(null);
                task.setDepth(0);
                projectTaskMapper.insert(task);
                taskCodeToIdMap.put(taskDef.getTaskCode(), task.getId());
            }
            // 6.2 第二遍：回填 parentId/rootId/path/depth
            for (TemplateSnapshot.TaskDef taskDef : snapshot.getTasks()) {
                Long taskId = taskCodeToIdMap.get(taskDef.getTaskCode());
                ProjectTaskDO updateTask = new ProjectTaskDO();
                updateTask.setId(taskId);
                if (taskDef.getParentTaskCode() == null || taskDef.getParentTaskCode().isEmpty()) {
                    // 顶层任务
                    updateTask.setParentId(null);
                    updateTask.setRootId(taskId);
                    updateTask.setPath("/" + taskId + "/");
                    updateTask.setDepth(0);
                } else {
                    Long parentId = taskCodeToIdMap.get(taskDef.getParentTaskCode());
                    if (parentId == null) {
                        throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                                "任务【" + taskDef.getTaskCode() + "】的父任务编码【" + taskDef.getParentTaskCode() + "】不存在");
                    }
                    // 查询父任务获取 rootId/path/depth
                    ProjectTaskDO parentTask = projectTaskMapper.selectById(parentId);
                    updateTask.setParentId(parentId);
                    updateTask.setRootId(parentTask.getRootId());
                    updateTask.setPath(parentTask.getPath() + taskId + "/");
                    updateTask.setDepth(parentTask.getDepth() + 1);
                }
                projectTaskMapper.updateById(updateTask);
            }
        }

        // 7. 批量创建团队角色（待分配人员）
        if (snapshot.getTeamRoles() != null) {
            for (TemplateSnapshot.TeamRoleDef roleDef : snapshot.getTeamRoles()) {
                ProjectTeamMemberDO member = new ProjectTeamMemberDO();
                member.setProjectId(project.getId());
                member.setUserId(null); // 待分配
                member.setRoleCode(roleDef.getRoleCode());
                member.setRoleName(roleDef.getRoleName());
                member.setStatus(0); // 启用
                projectTeamMemberMapper.insert(member);
            }
        }

        return project.getId();
    }

    private void validateExists(Long id) {
        if (id == null || projectTemplateMapper.selectById(id) == null) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        ProjectTemplateDO existing = projectTemplateMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(PROJECT_TEMPLATE_CODE_DUPLICATE);
        }
    }

    /**
     * 校验快照完整性
     */
    private void validateSnapshot(TemplateSnapshot snapshot) {
        if (snapshot == null) {
            throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "快照内容为空");
        }
        if (snapshot.getPhases() == null || snapshot.getPhases().isEmpty()) {
            throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "阶段定义不能为空");
        }
        // 校验阶段编码唯一
        Set<String> phaseCodes = new HashSet<>();
        for (TemplateSnapshot.PhaseDef phase : snapshot.getPhases()) {
            if (phase.getPhaseCode() == null || phase.getPhaseCode().isEmpty()) {
                throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "阶段编码不能为空");
            }
            if (!phaseCodes.add(phase.getPhaseCode())) {
                throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                        "阶段编码【" + phase.getPhaseCode() + "】重复");
            }
        }
        // 校验任务编码唯一与引用完整性
        if (snapshot.getTasks() != null) {
            Set<String> taskCodes = new HashSet<>();
            for (TemplateSnapshot.TaskDef task : snapshot.getTasks()) {
                if (task.getTaskCode() == null || task.getTaskCode().isEmpty()) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "任务编码不能为空");
                }
                if (!taskCodes.add(task.getTaskCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "任务编码【" + task.getTaskCode() + "】重复");
                }
                if (task.getPhaseCode() != null && !task.getPhaseCode().isEmpty()
                        && !phaseCodes.contains(task.getPhaseCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "任务【" + task.getTaskCode() + "】引用的阶段编码【" + task.getPhaseCode() + "】不存在");
                }
                if (task.getParentTaskCode() != null && !task.getParentTaskCode().isEmpty()
                        && task.getParentTaskCode().equals(task.getTaskCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "任务【" + task.getTaskCode() + "】不能以自身为父任务");
                }
            }
            // 二次遍历校验 parentTaskCode 引用存在
            for (TemplateSnapshot.TaskDef task : snapshot.getTasks()) {
                if (task.getParentTaskCode() != null && !task.getParentTaskCode().isEmpty()
                        && !taskCodes.contains(task.getParentTaskCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "任务【" + task.getTaskCode() + "】的父任务编码【" + task.getParentTaskCode() + "】不存在");
                }
            }
        }
        // 校验团队角色编码唯一
        if (snapshot.getTeamRoles() != null) {
            Set<String> roleCodes = new HashSet<>();
            for (TemplateSnapshot.TeamRoleDef role : snapshot.getTeamRoles()) {
                if (role.getRoleCode() == null || role.getRoleCode().isEmpty()) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "团队角色编码不能为空");
                }
                if (!roleCodes.add(role.getRoleCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "团队角色编码【" + role.getRoleCode() + "】重复");
                }
            }
        }
    }
}
