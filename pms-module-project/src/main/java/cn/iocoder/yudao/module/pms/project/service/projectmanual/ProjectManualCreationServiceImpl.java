package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCompanyDepartmentRelationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectCompanyDepartmentRelationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.MemberAssignmentRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectCodeRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectTreeRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService.DeliverableDefinition;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CREATE_FIELDS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_MEMBER_INTERVAL_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_AMBIGUOUS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NO_MATCH;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_SELECTABLE;

/**
 * 项目手工创建 Service 实现（F-PM01 / PM-01）
 * <p>
 * 创建与实例化单事务：BR-2 校验 → 模板选择/阻断（BR-3，阻断场景不烧编码流水）
 * → 编码分配（BR-8）→ 主档两段写入（INSERT 占位 code_root_id=0 后同事务 UPDATE 回填 id，
 * 保持 uk/不变量成立）→ 冻结版本实例化五要素+门禁引用 → 可选指派与下单办事处关系。
 * 消费 F-PM03 {@link ProjectTemplateService}（只读，不修改）。
 */
@Service
@Validated
public class ProjectManualCreationServiceImpl implements ProjectManualCreationService {

    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectStageInstanceMapper stageInstanceMapper;
    @Resource
    private ProjectTaskInstanceMapper taskInstanceMapper;
    @Resource
    private ProjectTaskExecutionContractMapper taskExecutionContractMapper;
    @Resource
    private ProjectMilestoneInstanceMapper milestoneInstanceMapper;
    @Resource
    private ProjectGateInstanceMapper gateInstanceMapper;
    @Resource
    private ProjectGateReferenceInstanceMapper gateReferenceInstanceMapper;
    @Resource
    private ProjectMemberAssignmentMapper memberAssignmentMapper;
    @Resource
    private ProjectCompanyDepartmentRelationMapper companyDepartmentRelationMapper;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private ProjectCodeAllocator projectCodeAllocator;
    @Resource
    private TaskExecutionContractFactory taskExecutionContractFactory;
    @Resource
    private ProjectDeliverableInitializationApplicationService deliverableInitializationApplicationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectMasterDO createProject(ProjectMasterDO draft, String orderOfficeCompanyCode,
                                         String orderOfficeDepartmentCode, Long manualTemplateId,
                                         Long serviceManagerUserId) {
        // a) BR-2 必填校验（失败不落库不实例化；子项目三维/模板继承父，仅名称+创建原因必填）
        List<String> missing = draft.getParentId() == null
                ? ProjectRules.validateManualCreation(draft)
                : ProjectRules.validateChildCreation(draft);
        if (!missing.isEmpty()) {
            throw exception(PROJECT_CREATE_FIELDS_INVALID, String.join("、", missing));
        }
        // b) 模板选择与冻结内容读取（根项目四维匹配/人工选择；子项目继承父模板版本）
        SelectedTemplate selected = draft.getParentId() == null
                ? selectTemplate(draft, manualTemplateId)
                : selectInheritedTemplate(draft.getParentId());
        TemplateDefinitionContent content =
                projectTemplateService.getRevisionContent(selected.templateId(), selected.revisionNo());
        // V1.8正式创建只能从唯一S0开始，且须在烧编码流水、写任何事实前阻断。
        TemplateInstantiator.requireSingleS0(content);
        // c) 编码分配（BR-8）+ 树真值（根项目/子项目分支）
        if (draft.getParentId() == null) {
            draft.setProjectCode(projectCodeAllocator.allocateRootCode());
            draft.setCodeRuleVersion(ProjectCodeRules.CODE_RULE_VERSION);
            draft.setProjectSequence(ProjectCodeRules.ROOT_PROJECT_SEQUENCE);
            draft.setTreePath(ProjectTreeRules.ROOT_PATH);
            draft.setTreeDepth(0);
            draft.setTreeSort(0);
        } else {
            ProjectMasterDO parent = validateProjectExists(draft.getParentId());
            ProjectCodeAllocator.ChildCodeAllocation allocation =
                    projectCodeAllocator.allocateChildCode(parent.getCodeRootId(), parent.getProjectCode());
            draft.setProjectCode(allocation.projectCode());
            draft.setCodeRuleVersion(ProjectCodeRules.CODE_RULE_VERSION);
            draft.setProjectSequence(allocation.projectSequence());
            draft.setCodeRootId(parent.getCodeRootId());
            draft.setRootId(parent.getRootId());
            draft.setTreePath(ProjectTreeRules.buildChildPath(parent.getTreePath(), parent.getId()));
            draft.setTreeDepth(ProjectTreeRules.buildChildDepth(parent.getTreeDepth()));
            draft.setTreeSort(0);
            inheritFromParent(draft, parent);
        }
        // d) 冻结模板引用（BR-4：绑定 revision 与流程定义版本写入主档）
        draft.setLifecycleTemplateId(selected.templateId());
        draft.setLifecycleTemplateRevisionNo(selected.revisionNo());
        draft.setTemplateLoadMethod(selected.loadMethod());
        draft.setProcessDefinitionKey(content.getProcessDefinitionKey());
        draft.setProcessDefinitionVersion(content.getProcessDefinitionVersion());
        draft.setSourceType(ProjectRules.SOURCE_TYPE_MANUAL);
        draft.setStatus(ProjectRules.INITIAL_STATUS);
        draft.setLifecycleStatus(ProjectRules.LIFECYCLE_STATUS_ACTIVE);
        draft.setCurrentStage(ProjectRules.STATUS_S0);
        draft.setAssignmentStatus(ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED);
        // e) 主档写入：根项目两段（code_root_id=root_id=id）；子项目单段（继承父）
        if (draft.getParentId() == null) {
            draft.setCodeRootId(0L);
            draft.setRootId(0L);
            projectMasterMapper.insert(draft);
            ProjectMasterDO namespaceUpdate = new ProjectMasterDO();
            namespaceUpdate.setId(draft.getId());
            namespaceUpdate.setCodeRootId(draft.getId());
            namespaceUpdate.setRootId(draft.getId());
            projectMasterMapper.updateById(namespaceUpdate);
            draft.setCodeRootId(draft.getId());
            draft.setRootId(draft.getId());
        } else {
            projectMasterMapper.insert(draft);
        }
        // f) 冻结版本实例化五要素 + 门禁引用行（source_definition_id 无定义行ID时保持 NULL）
        ProjectInstantiation instantiation = TemplateInstantiator.instantiate(content, draft.getId());
        insertIfNotEmpty(instantiation.getStages(), stageInstanceMapper::insertBatch);
        // 逐条写入任务以取得稳定实例ID，再冻结一任务一当前执行契约。
        for (int index = 0; index < instantiation.getTasks().size(); index++) {
            var task = instantiation.getTasks().get(index);
            taskInstanceMapper.insert(task);
            TemplateDefinitionContent.TaskDef definition = content.getTasks().get(index);
            ProjectTaskExecutionContractDO contract = taskExecutionContractFactory.create(
                    task.getId(), definition.getId(), definition, LocalDateTime.now());
            taskExecutionContractMapper.insert(contract);
        }
        insertIfNotEmpty(instantiation.getMilestones(), milestoneInstanceMapper::insertBatch);
        List<DeliverableDefinition> deliverableDefinitions = content.getDeliverables().stream()
                .map(definition -> new DeliverableDefinition(
                        definition.getDeliverableCode(), definition.getName(), definition.getStageCode(),
                        definition.getTaskCode(), Boolean.TRUE.equals(definition.getRequired()), definition.getId()))
                .toList();
        deliverableInitializationApplicationService.initialize(new InitializeProjectDeliverablesCommand(
                draft.getId(), selected.revisionId(), deliverableDefinitions));
        // 门禁需先落库取自增 id，供引用行回填 gate_id
        instantiation.getGates().forEach(gateInstanceMapper::insert);
        for (ProjectGateInstanceDO gate : instantiation.getGates()) {
            List<ProjectGateReferenceInstanceDO> references =
                    instantiation.getGateReferencesByGateCode().get(gate.getGateCode());
            if (references == null) {
                continue;
            }
            for (ProjectGateReferenceInstanceDO reference : references) {
                reference.setGateId(gate.getId());
                gateReferenceInstanceMapper.insert(reference);
            }
        }
        // g) 可选服务经理指派（一级 SERVICE_MANAGER_L1；PROJECT_MANAGER 不写）
        if (serviceManagerUserId != null) {
            doAssignServiceManager(draft.getId(), serviceManagerUserId, null, null, LocalDateTime.now());
        }
        // h) 下单办事处关系（relation_role=ORDER_OFFICE，is_primary=1，effective_from=now）
        if (orderOfficeCompanyCode != null && !orderOfficeCompanyCode.isBlank()) {
            ProjectCompanyDepartmentRelationDO relation = new ProjectCompanyDepartmentRelationDO();
            relation.setProjectId(draft.getId());
            relation.setCompanyCode(orderOfficeCompanyCode);
            relation.setDepartmentCode(orderOfficeDepartmentCode);
            relation.setRelationRole(ProjectRules.RELATION_ROLE_ORDER_OFFICE);
            relation.setIsPrimary(Boolean.TRUE);
            relation.setEffectiveFrom(LocalDateTime.now());
            relation.setStatus(ProjectRules.RELATION_STATUS_ACTIVE);
            companyDepartmentRelationMapper.insert(relation);
        }
        return draft;
    }

    @Override
    public void updateProject(ProjectMasterDO update) {
        if (update == null || update.getId() == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectMasterDO current = validateProjectExists(update.getId());
        // BR-7：不可变字段以库内值为准（更新载荷中的不可变字段值被忽略）
        ProjectRules.applyImmutableFields(update, current);
        projectMasterMapper.updateById(update);
    }

    @Override
    public ProjectMasterDO getProject(Long id) {
        return projectMasterMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectMasterDO> getProjectPage(PageParam pageParam, String projectName, String projectCode,
                                                      String status, String signingMethod, String projectCategory,
                                                      String implementationMode) {
        return projectMasterMapper.selectPage(pageParam, projectName, projectCode, status,
                signingMethod, projectCategory, implementationMode);
    }

    @Override
    public ProjectInstantiation getInstances(Long projectId) {
        validateProjectExists(projectId);
        ProjectInstantiation view = new ProjectInstantiation();
        view.setStages(stageInstanceMapper.selectListByProjectId(projectId));
        view.setTasks(taskInstanceMapper.selectListByProjectId(projectId));
        view.setMilestones(milestoneInstanceMapper.selectListByProjectId(projectId));
        view.setDeliverables(deliverableInitializationApplicationService.getByProjectId(projectId).stream()
                .map(deliverable -> {
                    var legacyView = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectDeliverableInstanceDO();
                    legacyView.setId(deliverable.id());
                    legacyView.setProjectId(deliverable.projectId());
                    legacyView.setDeliverableCode(deliverable.deliverableCode());
                    legacyView.setName(deliverable.name());
                    legacyView.setStageCode(deliverable.stageCode());
                    legacyView.setTaskCode(deliverable.taskCode());
                    legacyView.setRequired(deliverable.required());
                    legacyView.setSourceDefinitionId(deliverable.sourceDefinitionId());
                    legacyView.setStatus(deliverable.status());
                    legacyView.setVersion(deliverable.version());
                    return legacyView;
                }).toList());
        view.setGates(gateInstanceMapper.selectListByProjectId(projectId));
        // 引用行按门禁实例分组回填到载体（实例视图复用实例化载体）
        if (!view.getGates().isEmpty()) {
            List<Long> gateIds = view.getGates().stream()
                    .map(ProjectGateInstanceDO::getId).toList();
            gateReferenceInstanceMapper.selectListByGateIds(gateIds).forEach(reference ->
                    view.getGateReferencesByGateCode()
                            .computeIfAbsent(gateCodeOf(view, reference.getGateId()), code -> new ArrayList<>())
                            .add(reference));
        }
        return view;
    }

    @Override
    public List<ProjectMemberAssignmentDO> getMemberAssignments(Long projectId) {
        validateProjectExists(projectId);
        return memberAssignmentMapper.selectListByProjectId(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignServiceManager(Long projectId, Long userId, String employeeNo, String name,
                                     LocalDateTime effectiveFrom) {
        validateProjectExists(projectId);
        doAssignServiceManager(projectId, userId, employeeNo, name, effectiveFrom);
    }

    // ========== 内部方法 ==========

    /**
     * 模板选择（BR-3/BR-4）：manualTemplateId 非空=MANUAL_SELECTED（校验 ACTIVE 且有 PUBLISHED 版本）；
     * 为空=四维 matchPreview 自动匹配：MATCHED→AUTO_DEFAULT；NO_MATCH/MULTI_MATCH→阻断并携带冲突清单。
     */
    private SelectedTemplate selectTemplate(ProjectMasterDO draft, Long manualTemplateId) {
        if (manualTemplateId != null) {
            ProjectTemplateDO template = projectTemplateService.getProjectTemplate(manualTemplateId);
            if (template == null || !TemplateRules.STATUS_ACTIVE.equals(template.getStatus())) {
                throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
            }
            ProjectTemplateRevisionDO revision = latestPublishedRevision(manualTemplateId);
            if (revision == null) {
                throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
            }
            return new SelectedTemplate(manualTemplateId, revision.getId(), revision.getRevisionNo(),
                    ProjectRules.TEMPLATE_LOAD_MANUAL_SELECTED);
        }
        TemplateMatchResult match = projectTemplateService.matchPreview(
                draft.getSigningMethod(), draft.getProjectCategory(),
                draft.getImplementationMode(), draft.getMajorProjectLevel());
        if (match.getOutcome() == TemplateMatchResult.Outcome.NO_MATCH) {
            throw exception(PROJECT_TEMPLATE_NO_MATCH, String.join("；", match.getConflicts()));
        }
        if (match.getOutcome() == TemplateMatchResult.Outcome.MULTI_MATCH) {
            throw exception(PROJECT_TEMPLATE_AMBIGUOUS, String.join("；", match.getConflicts()));
        }
        Long templateId = match.getMatched().getTemplateId();
        ProjectTemplateRevisionDO revision = latestPublishedRevision(templateId);
        if (revision == null) {
            throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
        }
        return new SelectedTemplate(templateId, revision.getId(), revision.getRevisionNo(),
                ProjectRules.TEMPLATE_LOAD_AUTO_DEFAULT);
    }

    /**
     * 子项目继承父项目的冻结模板版本（BR-2：继承拆分时指定的模板版本）。
     */
    private SelectedTemplate selectInheritedTemplate(Long parentId) {
        ProjectMasterDO parent = validateProjectExists(parentId);
        if (parent.getLifecycleTemplateId() == null || parent.getLifecycleTemplateRevisionNo() == null) {
            throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
        }
        // 子项目通过父项目继承模板版本，等价人工指定（不做四维匹配）
        ProjectTemplateRevisionDO revision = projectTemplateService.getRevisionList(parent.getLifecycleTemplateId())
                .stream()
                .filter(candidate -> parent.getLifecycleTemplateRevisionNo().equals(candidate.getRevisionNo()))
                .filter(candidate -> TemplateRules.REVISION_STATUS_PUBLISHED.equals(candidate.getStatus()))
                .findFirst().orElseThrow(() -> exception(PROJECT_TEMPLATE_NOT_SELECTABLE));
        return new SelectedTemplate(parent.getLifecycleTemplateId(), revision.getId(), revision.getRevisionNo(),
                ProjectRules.TEMPLATE_LOAD_MANUAL_SELECTED);
    }

    /**
     * 子项目继承父项目的可继承主数据（客户/公司/部门/四维），仅当 draft 未提供时继承。
     */
    private void inheritFromParent(ProjectMasterDO draft, ProjectMasterDO parent) {
        if (draft.getCustomerId() == null) draft.setCustomerId(parent.getCustomerId());
        if (draft.getCustomerCode() == null) draft.setCustomerCode(parent.getCustomerCode());
        if (draft.getCustomerName() == null) draft.setCustomerName(parent.getCustomerName());
        if (draft.getSigningMethod() == null) draft.setSigningMethod(parent.getSigningMethod());
        if (draft.getProjectCategory() == null) draft.setProjectCategory(parent.getProjectCategory());
        if (draft.getImplementationMode() == null) draft.setImplementationMode(parent.getImplementationMode());
        if (draft.getMajorProjectLevel() == null) draft.setMajorProjectLevel(parent.getMajorProjectLevel());
        if (draft.getCompanyCode() == null) draft.setCompanyCode(parent.getCompanyCode());
        if (draft.getCompanyName() == null) draft.setCompanyName(parent.getCompanyName());
        if (draft.getDepartmentCode() == null) draft.setDepartmentCode(parent.getDepartmentCode());
        if (draft.getDepartmentName() == null) draft.setDepartmentName(parent.getDepartmentName());
    }

    /**
     * 模板最新 PUBLISHED 版本号（草稿 revision_no=0 不参与）。
     */
    private ProjectTemplateRevisionDO latestPublishedRevision(Long templateId) {
        return projectTemplateService.getRevisionList(templateId).stream()
                .filter(revision -> TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus()))
                .filter(revision -> revision.getRevisionNo() != null && revision.getRevisionNo() > 0)
                .max(java.util.Comparator.comparing(ProjectTemplateRevisionDO::getRevisionNo))
                .orElse(null);
    }

    /**
     * 指派一级服务经理：与新区间重叠的旧区间关闭（effective_to=新区间起点，边界相接不重叠）+ 开新区间。
     */
    private void doAssignServiceManager(Long projectId, Long userId, String employeeNo, String name,
                                        LocalDateTime effectiveFrom) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = effectiveFrom != null ? effectiveFrom : now;
        if (!MemberAssignmentRules.canStartIntervalAt(from, now)) {
            throw exception(PROJECT_MEMBER_INTERVAL_CONFLICT);
        }
        List<ProjectMemberAssignmentDO> existing = memberAssignmentMapper.selectListByRole(
                projectId, userId, ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1);
        for (ProjectMemberAssignmentDO assignment : existing) {
            if (!MemberAssignmentRules.intervalsOverlap(assignment.getEffectiveFrom(),
                    assignment.getEffectiveTo(), from, null)) {
                continue;
            }
            if (!MemberAssignmentRules.canCloseAt(from, assignment.getEffectiveFrom())) {
                throw exception(PROJECT_MEMBER_INTERVAL_CONFLICT);
            }
            ProjectMemberAssignmentDO close = new ProjectMemberAssignmentDO();
            close.setId(assignment.getId());
            close.setEffectiveTo(from);
            memberAssignmentMapper.updateById(close);
        }
        ProjectMemberAssignmentDO fresh = new ProjectMemberAssignmentDO();
        fresh.setProjectId(projectId);
        fresh.setUserId(userId);
        fresh.setEmployeeNo(employeeNo);
        fresh.setMemberName(name);
        fresh.setMemberRole(ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1);
        fresh.setEffectiveFrom(from);
        fresh.setStatus(MemberAssignmentRules.STATUS_ACTIVE);
        memberAssignmentMapper.insert(fresh);
    }

    private ProjectMasterDO validateProjectExists(Long id) {
        ProjectMasterDO project = projectMasterMapper.selectById(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return project;
    }

    private String gateCodeOf(ProjectInstantiation view, Long gateId) {
        return view.getGates().stream()
                .filter(gate -> gateId != null && gateId.equals(gate.getId()))
                .map(ProjectGateInstanceDO::getGateCode)
                .findFirst().orElse(null);
    }

    private <T> void insertIfNotEmpty(List<T> entities, Consumer<List<T>> inserter) {
        if (entities != null && !entities.isEmpty()) {
            inserter.accept(entities);
        }
    }

    /**
     * 模板选择结果（冻结上下文：templateId/revisionNo/加载方式）
     */
    private record SelectedTemplate(Long templateId, Long revisionId, Integer revisionNo, String loadMethod) {
    }
}
