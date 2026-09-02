package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
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
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.VisibleProjectPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentMemberResponsibilityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.MemberAssignmentRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectCodeRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectTreeRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeResolutionService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService.DeliverableDefinition;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.AcceptanceActivityInitializationApi;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateResolveQuery;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CREATE_FIELDS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_MEMBER_INTERVAL_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_SELECTABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_PROJECTION_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;

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
    private AcceptanceActivityInitializationApi acceptanceActivityInitializationApi;
    @Resource
    private SatisfactionQuestionnaireTemplateApi satisfactionQuestionnaireTemplateApi;
    @Resource
    private ProjectTaskTreePathMapper taskTreePathMapper;
    @Resource
    private TaskStateMachineMapper taskStateMachineMapper;
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
    private ProjectAttributeResolutionService projectAttributeResolutionService;
    @Resource
    private ProjectCodeAllocator projectCodeAllocator;
    @Resource
    private TaskExecutionContractFactory taskExecutionContractFactory;
    @Resource
    private ProjectDeliverableInitializationApplicationService deliverableInitializationApplicationService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private ProjectTreeVersionMapper projectTreeVersionMapper;
    @Resource
    private ProjectTreeScopeService projectTreeScopeService;
    @Resource
    private CustomerQueryApi customerQueryApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectMasterDO createProject(ProjectMasterDO draft, String orderOfficeCompanyCode,
                                         String orderOfficeDepartmentCode, Long templateRevisionId,
                                         String candidateWatermark, Long serviceManagerUserId) {
        List<String> missing = draft.getParentId() == null
                ? ProjectRules.validateManualCreation(draft)
                : ProjectRules.validateChildCreation(draft);
        if (!missing.isEmpty()) {
            throw exception(PROJECT_CREATE_FIELDS_INVALID, String.join("、", missing));
        }
        TemplateMatchDecision matchDecision = draft.getParentId() == null
                ? projectAttributeResolutionService.resolveInitial(new ProjectAttributeSnapshot(
                        draft.getSigningMethod(), draft.getProjectCategory(), draft.getImplementationMode(),
                        draft.getMajorProjectLevel()), templateRevisionId, candidateWatermark)
                : null;
        return createProject(draft, orderOfficeCompanyCode, orderOfficeDepartmentCode,
                matchDecision, serviceManagerUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectMasterDO createProject(ProjectMasterDO draft, String orderOfficeCompanyCode,
                                         String orderOfficeDepartmentCode, TemplateMatchDecision matchDecision,
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
                ? selectTemplate(matchDecision)
                : selectInheritedTemplate(draft.getParentId());
        TemplateDefinitionContent content =
                projectTemplateService.getRevisionContent(selected.templateId(), selected.revisionNo());
        // V1.8正式创建只能从唯一S0开始，且须在烧编码流水、写任何事实前阻断。
        TemplateInstantiator.requireSingleS0(content);
        LocalDateTime instantiationTime = LocalDateTime.now();
        Long trustedTenantId = draft.getTenantId();
        if (trustedTenantId == null || trustedTenantId < 0) {
            throw new IllegalArgumentException("项目受信租户不能为空");
        }
        var stateMachineRevision = taskStateMachineMapper.selectCurrentPublished(
                TaskStateMachinePublishedQuery.builder()
                        .tenantId(trustedTenantId)
                        .effectiveAt(instantiationTime)
                        .build());
        if (stateMachineRevision == null) {
            throw new IllegalStateException("当前租户没有生效的已发布任务状态机版本");
        }
        // c) 编码分配（BR-8）+ 树真值（根项目/子项目分支）
        if (draft.getParentId() == null) {
            validateCustomerAvailable(draft.getCustomerId());
            draft.setProjectType(ProjectRules.DEFAULT_PROJECT_TYPE);
            draft.setProjectCode(projectCodeAllocator.allocateRootCode());
            draft.setCodeRuleVersion(ProjectCodeRules.CODE_RULE_VERSION);
            draft.setProjectSequence(ProjectCodeRules.ROOT_PROJECT_SEQUENCE);
            draft.setTreePath(ProjectTreeRules.ROOT_PATH);
            draft.setTreeDepth(0);
            draft.setTreeSort(0);
        } else {
            ProjectMasterDO parent = validateProjectExists(draft.getParentId());
            inheritFromParent(draft, parent);
            validateCustomerAvailable(draft.getCustomerId());
            ProjectCodeAllocator.ChildCodeAllocation allocation =
                    projectCodeAllocator.allocateChildCode(parent.getCodeRootId(), parent.getProjectCode());
            draft.setProjectCode(allocation.projectCode());
            draft.setCodeRuleVersion(ProjectCodeRules.CODE_RULE_VERSION);
            draft.setProjectSequence(allocation.projectSequence());
            draft.setCodeRootId(parent.getCodeRootId());
            draft.setRootId(parent.getRootId());
            draft.setTreePath(ProjectTreeRules.buildChildPath(parent.getTreePath(), parent.getId()));
            draft.setTreeDepth(ProjectTreeRules.buildChildDepth(parent.getTreeDepth()));
            if (draft.getTreeSort() == null) {
                draft.setTreeSort(0);
            }
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
        draft.setVersion(0);
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
        ProjectInstantiation instantiation = TemplateInstantiator.instantiate(
                content, draft.getId(), stateMachineRevision.getId(), IdWorker::getId);
        insertIfNotEmpty(instantiation.getStages(), stageInstanceMapper::insertBatch);
        freezeSatisfactionFacts(draft, instantiation);
        // 任务ID已在落库前确定；先写完整任务集合，再写闭包和一任务一当前执行契约。
        instantiation.getTasks().forEach(taskInstanceMapper::insert);
        insertIfNotEmpty(instantiation.getTaskTreePaths(), taskTreePathMapper::insertBatch);
        Map<String, TemplateDefinitionContent.TaskDef> definitionsByCode = new LinkedHashMap<>();
        content.getTasks().stream().filter(Objects::nonNull)
                .forEach(definition -> definitionsByCode.put(definition.getTaskCode(), definition));
        List<PendingAcceptanceContract> pendingAcceptanceContracts = new ArrayList<>();
        for (var task : instantiation.getTasks()) {
            TemplateDefinitionContent.TaskDef definition = definitionsByCode.get(task.getTaskCode());
            if (definition == null) {
                throw new IllegalArgumentException("模板任务定义不存在：" + task.getTaskCode());
            }
            AcceptanceTaskMapping acceptanceMapping = acceptanceTaskMapping(task.getTaskCode());
            if (acceptanceMapping != null) {
                pendingAcceptanceContracts.add(new PendingAcceptanceContract(task.getId(), definition,
                        IdWorker.getId(), acceptanceMapping));
                continue;
            }
            ProjectTaskExecutionContractDO contract = taskExecutionContractFactory.create(
                    task.getId(), definition.getId(), definition, instantiationTime);
            contract.setTenantId(draft.getTenantId());
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
        for (PendingAcceptanceContract pending : pendingAcceptanceContracts) {
            var initialized = acceptanceActivityInitializationApi.initialize(
                    new AcceptanceActivityInitializationCommand(draft.getTenantId(), draft.getId(),
                            pending.projectTaskId(), pending.definition().getTaskCode(), pending.contractId(),
                            pending.mapping().acceptanceType(), pending.mapping().deliverableCode(),
                            selected.revisionNo()));
            if (initialized == null || !"INITIALIZED".equals(initialized.outcome())
                    || initialized.acceptanceId() == null || initialized.activityVersion() == null) {
                throw new IllegalStateException("ACCEPTANCE_ACTIVITY_INITIALIZATION_FAILED");
            }
            ProjectTaskExecutionContractDO contract = taskExecutionContractFactory.createAcceptanceActivity(
                    pending.contractId(), pending.projectTaskId(), pending.definition().getId(),
                    initialized.acceptanceId(), pending.definition().getDefinitionVersion(), instantiationTime);
            contract.setTenantId(draft.getTenantId());
            taskExecutionContractMapper.insert(contract);
        }
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
            AssignServiceManagerCommand initialAssignment = new AssignServiceManagerCommand(
                    draft.getId(), draft.getVersion(), "L1", serviceManagerUserId, null,
                    ProjectRules.ASSIGNMENT_TYPE_PRIMARY, draft.getDepartmentId(), draft.getDepartmentCode(),
                    "项目创建时指定", null, null);
            doAssignServiceManager(initialAssignment, draft,
                    ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1, LocalDateTime.now());
        }
        // h) 下单办事处关系（relation_role=ORDER_OFFICE，is_primary=1，effective_from=now）
        if (orderOfficeCompanyCode != null && !orderOfficeCompanyCode.isBlank()) {
            ProjectCompanyDepartmentRelationDO relation = new ProjectCompanyDepartmentRelationDO();
            relation.setProjectId(draft.getId());
            relation.setCompanyId(draft.getCompanyId());
            relation.setCompanyCode(orderOfficeCompanyCode);
            relation.setCompanyName(draft.getCompanyName());
            relation.setDepartmentId(draft.getDepartmentId());
            relation.setDepartmentCode(orderOfficeDepartmentCode);
            relation.setDepartmentName(draft.getDepartmentName());
            relation.setRelationRole(ProjectRules.RELATION_ROLE_ORDER_OFFICE);
            relation.setIsPrimary(Boolean.TRUE);
            relation.setEffectiveFrom(LocalDateTime.now());
            relation.setStatus(ProjectRules.RELATION_STATUS_ACTIVE);
            companyDepartmentRelationMapper.insert(relation);
        }
        return draft;
    }

    @Override
    public void updateProject(ProjectMasterDO update, ProjectAccessActor actor) {
        if (update == null || update.getId() == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectMasterDO current = requireScopedProject(update.getId(), actor, ACTION_MANAGE);
        // BR-7：不可变字段以库内值为准（更新载荷中的不可变字段值被忽略）
        ProjectRules.applyImmutableFields(update, current);
        projectMasterMapper.updateById(update);
    }

    @Override
    public ProjectMasterDO getProject(Long id, ProjectAccessActor actor) {
        return requireScopedProject(id, actor, ACTION_VIEW);
    }

    @Override
    public ProjectMasterDO getProjectForManage(Long id, ProjectAccessActor actor) {
        return requireScopedProject(id, actor, ACTION_MANAGE);
    }

    @Override
    public PageResult<ProjectMasterDO> getProjectPage(PageParam pageParam, String projectName, String projectCode,
                                                      String status, String signingMethod, String projectCategory,
                                                      String implementationMode, ProjectAccessActor actor) {
        validateActor(actor);
        var visibleProjectIds = projectTreeScopeService.resolveAllFullProjectIds(
                actor.tenantId(), actor.actorId(), ACTION_VIEW);
        return projectMasterMapper.selectPage(new VisibleProjectPageQuery(
                actor.tenantId(), visibleProjectIds, pageParam, projectName, projectCode, status,
                signingMethod, projectCategory, implementationMode));
    }

    @Override
    public ProjectInstantiation getInstances(Long projectId, ProjectAccessActor actor) {
        requireScopedProject(projectId, actor, ACTION_VIEW);
        return loadInstances(projectId);
    }

    @Override
    public ProjectInstantiation getInstancesForCreation(Long projectId, Long tenantId) {
        ProjectMasterDO project = projectMasterMapper.selectById(projectId);
        if (project == null || !java.util.Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return loadInstances(projectId);
    }

    private ProjectInstantiation loadInstances(Long projectId) {
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
    public List<ProjectMemberAssignmentDO> getMemberAssignments(Long projectId, ProjectAccessActor actor) {
        requireScopedProject(projectId, actor, ACTION_VIEW);
        return memberAssignmentMapper.selectListByProjectId(projectId);
    }

    private ProjectMasterDO requireScopedProject(Long projectId, ProjectAccessActor actor, String actionCode) {
        validateActor(actor);
        ProjectMasterDO project = projectMasterMapper.selectById(projectId);
        if (project == null || !java.util.Objects.equals(project.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO active = projectTreeVersionMapper.selectLatestActive(rootId);
        if (active == null) {
            throw exception(PROJECT_TREE_PROJECTION_UNAVAILABLE);
        }
        var scope = projectTreeScopeService.resolve(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, actionCode, active.getTreeVersion()));
        if (!scope.fullProjectIds().contains(projectId)) {
            if (ACTION_VIEW.equals(actionCode)) {
                throw exception(PROJECT_NOT_EXISTS);
            }
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return project;
    }

    private void validateActor(ProjectAccessActor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignServiceManagerResult assignServiceManager(AssignServiceManagerCommand command) {
        validateAssignmentCommand(command);
        ProjectMasterDO project = validateProjectExists(command.projectId());
        if (projectMasterMapper.incrementVersionIfMatch(command.projectId(), command.expectedVersion()) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        LocalDateTime effectiveFrom = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        String memberRole = "L1".equals(command.levelCode())
                ? ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1
                : ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L2;
        List<ProjectMemberAssignmentDO> activeBefore = memberAssignmentMapper.selectActiveForAssignmentState(
                new ProjectAssignmentStateQuery(command.projectId(), effectiveFrom));
        Long previousPrimaryManagerId = currentPrimaryServiceManager(activeBefore, memberRole, command.siteId());
        ProjectMemberAssignmentDO assignment = doAssignServiceManager(
                command, project, memberRole, effectiveFrom);
        int newVersion = command.expectedVersion() + 1;
        List<ProjectMemberAssignmentDO> activeAfter = memberAssignmentMapper.selectActiveForAssignmentState(
                new ProjectAssignmentStateQuery(command.projectId(), effectiveFrom));
        String assignmentStatus = calculateAssignmentStatus(activeAfter);
        String storedStatus = project.getAssignmentStatus() == null
                ? ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED : project.getAssignmentStatus();
        if (!Objects.equals(storedStatus, assignmentStatus)
                && projectMasterMapper.updateAssignmentStatusIfVersion(
                new ProjectAssignmentStatusUpdate(command.projectId(), newVersion, assignmentStatus)) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        Long currentPrimaryManagerId = ProjectRules.ASSIGNMENT_TYPE_PRIMARY.equals(command.assignmentType())
                ? command.managerId() : currentPrimaryServiceManager(activeAfter, memberRole, command.siteId());
        return new AssignServiceManagerResult(command.projectId(), assignment.getId(), newVersion,
                assignmentStatus, effectiveFrom, previousPrimaryManagerId, currentPrimaryManagerId);
    }

    // ========== 内部方法 ==========

    /**
     * 模板选择（BR-FPROJ-003）：提交时重算候选水位；显式选择必须是当前候选中的发布revision，
     * 未显式选择时仅允许唯一默认候选。禁止按模板ID重新挑选latest revision。
     */
    private SelectedTemplate selectTemplate(TemplateMatchDecision matchDecision) {
        TemplateMatchDecisionRules.validateInitialDecision(matchDecision);
        Long revisionId = matchDecision.matchedTemplateRevisionId();
        ProjectTemplateRevisionDO revision = projectTemplateService.getRevisionById(revisionId);
        ProjectTemplateDO template = revision == null ? null
                : projectTemplateService.getProjectTemplate(revision.getTemplateId());
        if (revision == null || template == null
                || !TemplateRules.STATUS_ACTIVE.equals(template.getStatus())
                || !TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus())
                || !matchDecision.matchedTemplateId().equals(revision.getTemplateId())) {
            throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
        }
        String loadMethod = TemplateMatchDecisionRules.DECISION_AUTO_UNIQUE.equals(matchDecision.decisionMode())
                ? ProjectRules.TEMPLATE_LOAD_AUTO_DEFAULT : ProjectRules.TEMPLATE_LOAD_MANUAL_SELECTED;
        return new SelectedTemplate(revision.getTemplateId(), revision.getId(), revision.getRevisionNo(),
                loadMethod);
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
        if (draft.getProjectType() == null) draft.setProjectType(parent.getProjectType());
        if (draft.getCompanyCode() == null) draft.setCompanyCode(parent.getCompanyCode());
        if (draft.getCompanyName() == null) draft.setCompanyName(parent.getCompanyName());
        if (draft.getDepartmentCode() == null) draft.setDepartmentCode(parent.getDepartmentCode());
        if (draft.getDepartmentName() == null) draft.setDepartmentName(parent.getDepartmentName());
    }

    /**
     * 指派一级服务经理：与新区间重叠的旧区间关闭（effective_to=新区间起点，边界相接不重叠）+ 开新区间。
     */
    private ProjectMemberAssignmentDO doAssignServiceManager(AssignServiceManagerCommand command,
                                                             ProjectMasterDO project, String memberRole,
                                                             LocalDateTime effectiveFrom) {
        List<ProjectMemberAssignmentDO> existing = memberAssignmentMapper.selectCurrentResponsibilityForUpdate(
                new CurrentMemberResponsibilityQuery(command.projectId(), memberRole,
                        command.assignmentType(), command.siteId(), effectiveFrom));
        for (ProjectMemberAssignmentDO assignment : existing) {
            if (ProjectRules.ASSIGNMENT_TYPE_COLLABORATOR.equals(command.assignmentType())) {
                if (Objects.equals(assignment.getUserId(), command.managerId())) {
                    throw exception(PROJECT_MEMBER_INTERVAL_CONFLICT);
                }
                continue;
            }
            if (!MemberAssignmentRules.canCloseAt(effectiveFrom, assignment.getEffectiveFrom())) {
                throw exception(PROJECT_MEMBER_INTERVAL_CONFLICT);
            }
            ProjectMemberAssignmentDO close = new ProjectMemberAssignmentDO();
            close.setId(assignment.getId());
            close.setEffectiveTo(effectiveFrom);
            memberAssignmentMapper.updateById(close);
        }
        ProjectMemberAssignmentDO fresh = new ProjectMemberAssignmentDO();
        fresh.setProjectId(command.projectId());
        fresh.setUserId(command.managerId());
        AdminUserRespDTO manager = adminUserApi.getUser(command.managerId());
        DeptRespDTO department = deptApi.getDept(command.departmentId());
        fresh.setMemberName(manager == null ? null : manager.getNickname());
        fresh.setCompanyId(project.getCompanyId());
        fresh.setCompanyCode(project.getCompanyCode());
        fresh.setCompanyName(project.getCompanyName());
        fresh.setDepartmentId(command.departmentId());
        fresh.setDepartmentCode(command.departmentCode());
        fresh.setDepartmentName(department == null ? null : department.getName());
        fresh.setMemberRole(memberRole);
        fresh.setAssignmentType(command.assignmentType());
        fresh.setSiteId(command.siteId());
        fresh.setResponsibility(command.levelCode());
        fresh.setChangeReason(command.changeReason().trim());
        fresh.setEffectiveFrom(effectiveFrom);
        fresh.setStatus(MemberAssignmentRules.STATUS_ACTIVE);
        memberAssignmentMapper.insert(fresh);
        return fresh;
    }

    private void validateAssignmentCommand(AssignServiceManagerCommand command) {
        if (command == null || command.projectId() == null || command.expectedVersion() == null
                || command.expectedVersion() < 0 || command.managerId() == null
                || command.departmentId() == null
                || command.departmentCode() == null
                || command.departmentCode().isBlank()
                || !("L1".equals(command.levelCode()) || "L2".equals(command.levelCode()))
                || !(ProjectRules.ASSIGNMENT_TYPE_PRIMARY.equals(command.assignmentType())
                || ProjectRules.ASSIGNMENT_TYPE_COLLABORATOR.equals(command.assignmentType()))
                || command.changeReason() == null || command.changeReason().isBlank()
                || command.changeReason().trim().length() > 500
                || ("L2".equals(command.levelCode()) && command.siteId() == null)) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "角色、层级、人员或版本无效");
        }
    }

    private String calculateAssignmentStatus(List<ProjectMemberAssignmentDO> active) {
        boolean projectManagerPresent = active.stream().anyMatch(assignment ->
                ProjectRules.MEMBER_ROLE_PROJECT_MANAGER.equals(assignment.getMemberRole()));
        boolean primaryServiceManagerPresent = active.stream().anyMatch(assignment ->
                isServiceManager(assignment.getMemberRole())
                        && (assignment.getAssignmentType() == null
                        || ProjectRules.ASSIGNMENT_TYPE_PRIMARY.equals(assignment.getAssignmentType())));
        return projectManagerPresent && primaryServiceManagerPresent
                ? ProjectRules.ASSIGNMENT_STATUS_ASSIGNED : ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED;
    }

    private Long currentPrimaryServiceManager(List<ProjectMemberAssignmentDO> active,
                                              String memberRole, Long siteId) {
        return active.stream().filter(assignment -> Objects.equals(memberRole, assignment.getMemberRole()))
                .filter(assignment -> Objects.equals(siteId, assignment.getSiteId()))
                .filter(assignment -> assignment.getAssignmentType() == null
                        || ProjectRules.ASSIGNMENT_TYPE_PRIMARY.equals(assignment.getAssignmentType()))
                .map(ProjectMemberAssignmentDO::getUserId).findFirst().orElse(null);
    }

    private boolean isServiceManager(String memberRole) {
        return ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1.equals(memberRole)
                || ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L2.equals(memberRole);
    }

    private ProjectMasterDO validateProjectExists(Long id) {
        ProjectMasterDO project = projectMasterMapper.selectById(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return project;
    }

    private void validateCustomerAvailable(Long customerId) {
        if (customerId == null) {
            return;
        }
        CustomerSummaryDTO customer = customerQueryApi.getCustomer(customerId);
        if (customer == null || !CustomerLifecycleStatus.ENABLED.name().equals(customer.lifecycleStatus())) {
            throw exception(PROJECT_CUSTOMER_UNAVAILABLE);
        }
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

    private void freezeSatisfactionFacts(ProjectMasterDO project, ProjectInstantiation instantiation) {
        for (var task : instantiation.getTasks()) {
            if (task.getSatisfactionTiming() == null || task.getSatisfactionTiming().isBlank()) {
                continue;
            }
            if (!"AFTER_INITIAL_ACCEPTANCE".equals(task.getSatisfactionTiming())) {
                throw new IllegalStateException("SATISFACTION_TIMING_OWNER_NOT_AVAILABLE");
            }
            SatisfactionTemplateFact fact = satisfactionQuestionnaireTemplateApi.resolvePublished(
                    new SatisfactionTemplateResolveQuery(project.getTenantId(), project.getProjectType(),
                            project.getSigningMethod(), project.getImplementationMode(), "ACCEPTANCE",
                            task.getSatisfactionTiming()));
            if (fact == null || !"FOUND".equals(fact.outcome()) || fact.templateId() == null
                    || fact.templateRevisionId() == null || fact.templateVersion() == null
                    || fact.ruleVersion() == null || fact.threshold() == null) {
                throw new IllegalStateException("SATISFACTION_TEMPLATE_NOT_UNIQUE");
            }
            task.setAccSatisfactionTemplateId(fact.templateId());
            task.setTemplateRevisionId(fact.templateRevisionId());
            task.setTemplateVersion(fact.templateVersion());
            task.setSatisfactionRuleVersion(fact.ruleVersion());
            task.setSatisfactionThreshold(fact.threshold());
        }
    }

    private AcceptanceTaskMapping acceptanceTaskMapping(String taskCode) {
        return switch (taskCode) {
            case "T-INITIAL-ACCEPT" -> new AcceptanceTaskMapping("PRELIMINARY", "D-INITIAL-REPORT");
            case "T-FINAL-ACCEPT" -> new AcceptanceTaskMapping("FINAL", "D-FINAL-REPORT");
            default -> null;
        };
    }

    private record AcceptanceTaskMapping(String acceptanceType, String deliverableCode) {
    }

    private record PendingAcceptanceContract(Long projectTaskId, TemplateDefinitionContent.TaskDef definition,
                                             Long contractId, AcceptanceTaskMapping mapping) {
    }
}
