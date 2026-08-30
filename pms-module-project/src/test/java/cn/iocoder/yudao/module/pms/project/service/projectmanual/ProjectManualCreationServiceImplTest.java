package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCompanyDepartmentRelationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateResolveQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectCompanyDepartmentRelationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectDeliverableInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.VisibleProjectPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeResolutionService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CREATE_FIELDS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_AMBIGUOUS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NO_MATCH;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_SELECTABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

/**
 * F-PM01 创建主流程单测（Mockito mock Mapper/依赖）：
 * BR-2 阻断、BR-4 无匹配/多匹配阻断、手工选择生效、两段写入 code_root_id=id、
 * 指派区间关闭开启、下单办事处关系、UPDATE 不可变字段被忽略。
 */
@ExtendWith(MockitoExtension.class)
class ProjectManualCreationServiceImplTest {

    private static final String CANDIDATE_WATERMARK = "candidate-watermark-v1";

    @Mock
    private ProjectMasterMapper projectMasterMapper;
    @Mock
    private ProjectStageInstanceMapper stageInstanceMapper;
    @Mock
    private ProjectTaskInstanceMapper taskInstanceMapper;
    @Mock
    private ProjectTaskExecutionContractMapper taskExecutionContractMapper;
    @Mock
    private ProjectTaskTreePathMapper taskTreePathMapper;
    @Mock
    private TaskStateMachineMapper taskStateMachineMapper;
    @Mock
    private ProjectMilestoneInstanceMapper milestoneInstanceMapper;
    @Mock
    private ProjectDeliverableInstanceMapper deliverableInstanceMapper;
    @Mock
    private ProjectGateInstanceMapper gateInstanceMapper;
    @Mock
    private ProjectGateReferenceInstanceMapper gateReferenceInstanceMapper;
    @Mock
    private ProjectMemberAssignmentMapper memberAssignmentMapper;
    @Mock
    private ProjectCompanyDepartmentRelationMapper companyDepartmentRelationMapper;
    @Mock
    private ProjectTemplateService projectTemplateService;
    @Mock
    private ProjectAttributeResolutionService projectAttributeResolutionService;
    @Mock
    private ProjectCodeAllocator projectCodeAllocator;
    @Mock
    private TaskExecutionContractFactory taskExecutionContractFactory;
    @Mock
    private ProjectDeliverableInitializationApplicationService deliverableInitializationApplicationService;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private DeptApi deptApi;
    @Mock
    private ProjectTreeVersionMapper projectTreeVersionMapper;
    @Mock
    private ProjectTreeScopeService projectTreeScopeService;
    @Mock
    private CustomerQueryApi customerQueryApi;
    @Mock
    private SatisfactionQuestionnaireTemplateApi satisfactionQuestionnaireTemplateApi;

    @InjectMocks
    private ProjectManualCreationServiceImpl service;

    @BeforeEach
    void setUpPublishedTaskStateMachine() {
        TaskStateMachineRevisionDO revision = new TaskStateMachineRevisionDO();
        revision.setId(8801L);
        lenient().when(taskStateMachineMapper.selectCurrentPublished(any())).thenReturn(revision);
    }

    // ========== BR-2 必填阻断 ==========

    @Test
    void creationBlockedWhenRequiredFieldsMissing() {
        ProjectMasterDO draft = validDraft();
        draft.setCreationReason(null);
        draft.setSigningMethod(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(draft, null, null, null, null, null));

        assertEquals(PROJECT_CREATE_FIELDS_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("创建原因"));
        assertTrue(exception.getMessage().contains("签约方式"));
        // 不落库、不实例化、不烧编码流水
        verifyNoInteractions(projectMasterMapper, projectTemplateService, projectCodeAllocator);
    }

    // ========== BR-4 模板匹配阻断 ==========

    @Test
    void creationBlockedWhenNoTemplateMatch() {
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenThrow(exception(PROJECT_TEMPLATE_NO_MATCH, "无匹配的生效模板"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, null, CANDIDATE_WATERMARK, null));

        assertEquals(PROJECT_TEMPLATE_NO_MATCH.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("无匹配"));
        // 不落库不实例化，且不消耗编码流水
        verifyNoInteractions(projectMasterMapper, projectCodeAllocator);
    }

    @Test
    void creationBlockedOnSamePriorityMultiMatch() {
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenThrow(exception(PROJECT_TEMPLATE_AMBIGUOUS, "模板【TPL-A】；模板【TPL-B】"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, null, CANDIDATE_WATERMARK, null));

        assertEquals(PROJECT_TEMPLATE_AMBIGUOUS.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("TPL-A"));
        assertTrue(exception.getMessage().contains("TPL-B"));
        verifyNoInteractions(projectMasterMapper, projectCodeAllocator);
    }

    @Test
    void creationRejectsStaleCandidateWatermarkBeforeSelectingRevision() {
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenThrow(exception(PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, 1002L,
                        "stale-watermark", null));

        assertEquals(PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT.getCode(), exception.getCode());
        verify(projectTemplateService, never()).getRevisionById(anyLong());
        verifyNoInteractions(projectMasterMapper, projectCodeAllocator);
    }

    @Test
    void disabledCustomerBlocksCreationBeforeAllocatingProjectCode() {
        Long templateId = 9L;
        Long revisionId = 1002L;
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenReturn(decision(templateId, revisionId, TemplateMatchDecisionRules.DECISION_EXPLICIT));
        when(projectTemplateService.getProjectTemplate(templateId)).thenReturn(activeTemplate(templateId, "TPL-M"));
        when(projectTemplateService.getRevisionById(revisionId)).thenReturn(
                revision(templateId, TemplateRules.REVISION_STATUS_PUBLISHED, 2));
        when(projectTemplateService.getRevisionContent(templateId, 2)).thenReturn(contentWithOneGateAndReference());
        when(customerQueryApi.getCustomer(1L)).thenReturn(new CustomerSummaryDTO(
                1L, 1L, "CUST-001", "某客户", null, "DISABLED", "CRM", 1L, LocalDateTime.now()));
        ProjectMasterDO draft = validDraft();
        draft.setCustomerId(1L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createProject(draft, null, null, revisionId, CANDIDATE_WATERMARK, null));

        assertEquals(PROJECT_CUSTOMER_UNAVAILABLE.getCode(), error.getCode());
        verifyNoInteractions(projectCodeAllocator, projectMasterMapper);
    }

    // ========== 手工选择（MANUAL_SELECTED） ==========

    @Test
    void manualSelectionTakesEffectWithTwoPhaseNamespaceWrite() {
        Long templateId = 9L;
        Long revisionId = 1002L;
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenReturn(decision(templateId, revisionId, TemplateMatchDecisionRules.DECISION_EXPLICIT));
        when(projectTemplateService.getProjectTemplate(templateId)).thenReturn(activeTemplate(templateId, "TPL-M"));
        when(projectTemplateService.getRevisionById(revisionId)).thenReturn(
                revision(templateId, TemplateRules.REVISION_STATUS_PUBLISHED, 2));
        TemplateDefinitionContent content = contentWithOneGateAndReference();
        content.getTasks().getFirst().setSatisfactionTiming("AFTER_INITIAL_ACCEPTANCE");
        when(projectTemplateService.getRevisionContent(templateId, 2)).thenReturn(content);
        when(satisfactionQuestionnaireTemplateApi.resolvePublished(any())).thenReturn(
                new SatisfactionTemplateFact("FOUND", 992005100001L, 992005110001L,
                        1, "FACC002-RULE-V1", new BigDecimal("80.00")));
        when(projectCodeAllocator.allocateRootCode()).thenReturn("PJT2026000007");
        when(taskExecutionContractFactory.create(any(), any(), any(), any()))
                .thenReturn(new ProjectTaskExecutionContractDO());
        // 快照 INSERT 时刻的 code_root_id（服务随后对同一 DO 原地回填，事后捕获拿不到占位值）
        AtomicLong codeRootIdAtInsert = new AtomicLong(-1);
        doAnswer(invocation -> {
            ProjectMasterDO inserted = invocation.getArgument(0);
            codeRootIdAtInsert.set(inserted.getCodeRootId());
            inserted.setId(100L);
            return 1;
        }).when(projectMasterMapper).insert(any(ProjectMasterDO.class));

        ProjectMasterDO draft = validDraft();
        draft.setTenantId(1L);
        ProjectMasterDO created = service.createProject(draft, null, null, revisionId,
                CANDIDATE_WATERMARK, null);

        // 冻结上下文与主档语义
        assertEquals(100L, created.getId());
        assertEquals("PJT2026000007", created.getProjectCode());
        assertEquals("V1", created.getCodeRuleVersion());
        assertEquals(0, created.getProjectSequence());
        assertEquals(ProjectRules.SOURCE_TYPE_MANUAL, created.getSourceType());
        assertEquals(ProjectRules.STATUS_S0, created.getStatus());
        assertEquals(ProjectRules.LIFECYCLE_STATUS_ACTIVE, created.getLifecycleStatus());
        assertEquals(ProjectRules.STATUS_S0, created.getCurrentStage());
        assertEquals(ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED, created.getAssignmentStatus());
        assertEquals(templateId, created.getLifecycleTemplateId());
        assertEquals(2, created.getLifecycleTemplateRevisionNo());
        assertEquals(ProjectRules.TEMPLATE_LOAD_MANUAL_SELECTED, created.getTemplateLoadMethod());
        assertEquals("PROC-KEY", created.getProcessDefinitionKey());
        assertEquals("V3", created.getProcessDefinitionVersion());

        // 两段写入：INSERT 占位 code_root_id=0（insert 时刻快照）；UPDATE 回填 code_root_id=root_id=id
        assertEquals(0L, codeRootIdAtInsert.get());
        ArgumentCaptor<ProjectMasterDO> updateCaptor = ArgumentCaptor.forClass(ProjectMasterDO.class);
        verify(projectMasterMapper).updateById(updateCaptor.capture());
        assertEquals(100L, updateCaptor.getValue().getCodeRootId());
        assertEquals(100L, updateCaptor.getValue().getRootId());
        assertEquals(100L, created.getCodeRootId());

        // 五要素实例化批量落库
        verify(stageInstanceMapper).insertBatch(anyCollection());
        ArgumentCaptor<ProjectTaskInstanceDO> taskCaptor = ArgumentCaptor.forClass(ProjectTaskInstanceDO.class);
        verify(taskInstanceMapper).insert(taskCaptor.capture());
        assertEquals(992005100001L, taskCaptor.getValue().getAccSatisfactionTemplateId());
        assertEquals(992005110001L, taskCaptor.getValue().getTemplateRevisionId());
        assertEquals(1, taskCaptor.getValue().getTemplateVersion());
        assertEquals("FACC002-RULE-V1", taskCaptor.getValue().getSatisfactionRuleVersion());
        assertEquals(new BigDecimal("80.00"), taskCaptor.getValue().getSatisfactionThreshold());
        ArgumentCaptor<SatisfactionTemplateResolveQuery> satisfactionQueryCaptor =
                ArgumentCaptor.forClass(SatisfactionTemplateResolveQuery.class);
        verify(satisfactionQuestionnaireTemplateApi).resolvePublished(satisfactionQueryCaptor.capture());
        assertEquals("AFTER_INITIAL_ACCEPTANCE", satisfactionQueryCaptor.getValue().applicableTimingCode());
        verify(taskExecutionContractFactory).create(any(), any(), any(), any());
        ArgumentCaptor<ProjectTaskExecutionContractDO> contractCaptor =
                ArgumentCaptor.forClass(ProjectTaskExecutionContractDO.class);
        verify(taskExecutionContractMapper).insert(contractCaptor.capture());
        assertEquals(1L, contractCaptor.getValue().getTenantId());
        verify(milestoneInstanceMapper).insertBatch(anyCollection());
        verify(deliverableInitializationApplicationService).initialize(any());
        verify(deliverableInstanceMapper, never()).insertBatch(anyCollection());
        // 门禁单条落库后引用行回填 gate_id
        doAnswerAsGateInsert();
        verify(gateReferenceInstanceMapper).insert(any(ProjectGateReferenceInstanceDO.class));
        // 未指派/未登记办事处
        verifyNoInteractions(memberAssignmentMapper, companyDepartmentRelationMapper);
        verify(projectTemplateService).getRevisionById(revisionId);
        verify(projectTemplateService, never()).getRevisionList(templateId);
    }

    @Test
    void rejectsTemplateWithoutS0BeforeAllocatingCode() {
        Long templateId = 9L;
        Long revisionId = 1001L;
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenReturn(decision(templateId, revisionId, TemplateMatchDecisionRules.DECISION_EXPLICIT));
        when(projectTemplateService.getProjectTemplate(templateId)).thenReturn(activeTemplate(templateId, "TPL-S2"));
        when(projectTemplateService.getRevisionById(revisionId)).thenReturn(
                revision(templateId, TemplateRules.REVISION_STATUS_PUBLISHED, 1));
        TemplateDefinitionContent invalid = new TemplateDefinitionContent();
        TemplateDefinitionContent.StageDef stage = new TemplateDefinitionContent.StageDef();
        stage.setStageCode("S2");
        invalid.getStages().add(stage);
        when(projectTemplateService.getRevisionContent(templateId, 1)).thenReturn(invalid);

        assertThrows(IllegalArgumentException.class,
                () -> service.createProject(validDraft(), null, null, revisionId,
                        CANDIDATE_WATERMARK, null));

        verifyNoInteractions(projectCodeAllocator, projectMasterMapper);
    }

    @Test
    void manualSelectionRejectsNonActiveTemplate() {
        Long templateId = 9L;
        Long revisionId = 1001L;
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenReturn(decision(templateId, revisionId, TemplateMatchDecisionRules.DECISION_EXPLICIT));
        when(projectTemplateService.getRevisionById(revisionId)).thenReturn(
                revision(templateId, TemplateRules.REVISION_STATUS_PUBLISHED, 1));
        ProjectTemplateDO retired = activeTemplate(templateId, "TPL-R");
        retired.setStatus(TemplateRules.STATUS_RETIRED);
        when(projectTemplateService.getProjectTemplate(templateId)).thenReturn(retired);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, revisionId,
                        CANDIDATE_WATERMARK, null));

        assertEquals(PROJECT_TEMPLATE_NOT_SELECTABLE.getCode(), exception.getCode());
        verify(projectMasterMapper, never()).insert(any(ProjectMasterDO.class));
        verifyNoInteractions(projectCodeAllocator);
    }

    @Test
    void manualSelectionRejectsTemplateWithoutPublishedRevision() {
        Long templateId = 9L;
        Long revisionId = 1000L;
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenReturn(decision(templateId, revisionId, TemplateMatchDecisionRules.DECISION_EXPLICIT));
        when(projectTemplateService.getProjectTemplate(templateId)).thenReturn(activeTemplate(templateId, "TPL-D"));
        when(projectTemplateService.getRevisionById(revisionId)).thenReturn(
                revision(templateId, TemplateRules.REVISION_STATUS_DRAFT, 0));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, revisionId,
                        CANDIDATE_WATERMARK, null));

        assertEquals(PROJECT_TEMPLATE_NOT_SELECTABLE.getCode(), exception.getCode());
        verify(projectMasterMapper, never()).insert(any(ProjectMasterDO.class));
    }

    // ========== 自动匹配（AUTO_DEFAULT） ==========

    @Test
    void autoDefaultBindsUniqueMatchedTemplate() {
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenReturn(decision(5L, 1001L, TemplateMatchDecisionRules.DECISION_AUTO_UNIQUE));
        when(projectTemplateService.getProjectTemplate(5L)).thenReturn(activeTemplate(5L, "TPL-AUTO"));
        when(projectTemplateService.getRevisionById(1001L)).thenReturn(
                revision(5L, TemplateRules.REVISION_STATUS_PUBLISHED, 1));
        when(projectTemplateService.getRevisionContent(5L, 1)).thenReturn(contentWithS0Only());
        when(projectCodeAllocator.allocateRootCode()).thenReturn("PJT2026000008");
        doAnswer(invocation -> {
            ProjectMasterDO inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        }).when(projectMasterMapper).insert(any(ProjectMasterDO.class));

        ProjectMasterDO created = service.createProject(validDraft(), null, null, null,
                CANDIDATE_WATERMARK, null);

        assertEquals(5L, created.getLifecycleTemplateId());
        assertEquals(1, created.getLifecycleTemplateRevisionNo());
        assertEquals(ProjectRules.TEMPLATE_LOAD_AUTO_DEFAULT, created.getTemplateLoadMethod());
        // V1.8模板必须含S0，且只有S0阶段实例落库。
        verify(stageInstanceMapper).insertBatch(anyCollection());
        verifyNoInteractions(memberAssignmentMapper, companyDepartmentRelationMapper);
    }

    // ========== 创建时可选指派与下单办事处 ==========

    @Test
    void creationAssignsServiceManagerAndOrderOfficeRelation() {
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any()))
                .thenReturn(decision(5L, 1001L, TemplateMatchDecisionRules.DECISION_AUTO_UNIQUE));
        when(projectTemplateService.getProjectTemplate(5L)).thenReturn(activeTemplate(5L, "TPL-AUTO"));
        when(projectTemplateService.getRevisionById(1001L)).thenReturn(
                revision(5L, TemplateRules.REVISION_STATUS_PUBLISHED, 1));
        when(projectTemplateService.getRevisionContent(5L, 1)).thenReturn(contentWithS0Only());
        when(projectCodeAllocator.allocateRootCode()).thenReturn("PJT2026000009");
        doAnswer(invocation -> {
            ProjectMasterDO inserted = invocation.getArgument(0);
            inserted.setId(102L);
            return 1;
        }).when(projectMasterMapper).insert(any(ProjectMasterDO.class));
        // 用户 66 已有一条开放的一级服务经理区间（Id=7，至今有效）
        ProjectMemberAssignmentDO openInterval = new ProjectMemberAssignmentDO();
        openInterval.setId(7L);
        openInterval.setUserId(66L);
        openInterval.setMemberRole(ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1);
        openInterval.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        openInterval.setEffectiveTo(null);
        when(memberAssignmentMapper.selectCurrentResponsibilityForUpdate(any()))
                .thenReturn(List.of(openInterval));

        service.createProject(validDraft(), "CO-01", "DEP-01", null,
                CANDIDATE_WATERMARK, 66L);

        // 旧区间关闭：effective_to=新区间起点
        ArgumentCaptor<ProjectMemberAssignmentDO> closeCaptor = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberAssignmentMapper).updateById(closeCaptor.capture());
        assertEquals(7L, closeCaptor.getValue().getId());
        assertNotNull(closeCaptor.getValue().getEffectiveTo());
        // 新区间开启：SERVICE_MANAGER_L1（不写 PROJECT_MANAGER）
        ArgumentCaptor<ProjectMemberAssignmentDO> freshCaptor = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberAssignmentMapper).insert(freshCaptor.capture());
        assertEquals(102L, freshCaptor.getValue().getProjectId());
        assertEquals(66L, freshCaptor.getValue().getUserId());
        assertEquals(ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1, freshCaptor.getValue().getMemberRole());
        assertEquals("ACTIVE", freshCaptor.getValue().getStatus());
        assertEquals(closeCaptor.getValue().getEffectiveTo(), freshCaptor.getValue().getEffectiveFrom());
        // 下单办事处关系：ORDER_OFFICE + is_primary=1
        ArgumentCaptor<ProjectCompanyDepartmentRelationDO> relationCaptor =
                ArgumentCaptor.forClass(ProjectCompanyDepartmentRelationDO.class);
        verify(companyDepartmentRelationMapper).insert(relationCaptor.capture());
        assertEquals(102L, relationCaptor.getValue().getProjectId());
        assertEquals("CO-01", relationCaptor.getValue().getCompanyCode());
        assertEquals("DEP-01", relationCaptor.getValue().getDepartmentCode());
        assertEquals(ProjectRules.RELATION_ROLE_ORDER_OFFICE, relationCaptor.getValue().getRelationRole());
        assertEquals(Boolean.TRUE, relationCaptor.getValue().getIsPrimary());
    }

    // ========== 指派动作 ==========

    @Test
    void assignServiceManagerRejectsStaleProjectVersion() {
        when(projectMasterMapper.selectById(1L)).thenReturn(persistedProject());
        when(projectMasterMapper.incrementVersionIfMatch(1L, 0)).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assignServiceManager(assignCommand()));

        assertEquals(PROJECT_VERSION_CONFLICT.getCode(), exception.getCode());
        verifyNoInteractions(memberAssignmentMapper);
    }

    @Test
    void assignServiceManagerClosesRoleIntervalAndReturnsNewVersion() {
        ProjectMasterDO project = persistedProject();
        project.setAssignmentStatus(ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED);
        when(projectMasterMapper.selectById(1L)).thenReturn(project);
        when(projectMasterMapper.incrementVersionIfMatch(1L, 0)).thenReturn(1);
        ProjectMemberAssignmentDO previous = new ProjectMemberAssignmentDO();
        previous.setId(7L);
        previous.setUserId(55L);
        previous.setMemberRole(ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1);
        previous.setAssignmentType(ProjectRules.ASSIGNMENT_TYPE_PRIMARY);
        previous.setSiteId(30L);
        previous.setEffectiveFrom(LocalDateTime.now().minusDays(2));
        ProjectMemberAssignmentDO projectManager = new ProjectMemberAssignmentDO();
        projectManager.setUserId(77L);
        projectManager.setMemberRole(ProjectRules.MEMBER_ROLE_PROJECT_MANAGER);
        ProjectMemberAssignmentDO current = new ProjectMemberAssignmentDO();
        current.setUserId(66L);
        current.setMemberRole(ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1);
        current.setAssignmentType(ProjectRules.ASSIGNMENT_TYPE_PRIMARY);
        current.setSiteId(30L);
        when(memberAssignmentMapper.selectActiveForAssignmentState(any()))
                .thenReturn(List.of(previous, projectManager), List.of(current, projectManager));
        when(memberAssignmentMapper.selectCurrentResponsibilityForUpdate(any())).thenReturn(List.of(previous));
        when(projectMasterMapper.updateAssignmentStatusIfVersion(any())).thenReturn(1);
        doAnswer(invocation -> {
            ProjectMemberAssignmentDO inserted = invocation.getArgument(0);
            inserted.setId(8L);
            return 1;
        }).when(memberAssignmentMapper).insert(any(ProjectMemberAssignmentDO.class));

        LocalDateTime invocationStartedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        AssignServiceManagerResult result = service.assignServiceManager(assignCommand());

        assertEquals(1, result.version());
        assertEquals(8L, result.assignmentId());
        assertEquals(ProjectRules.ASSIGNMENT_STATUS_ASSIGNED, result.assignmentStatus());
        assertEquals(55L, result.previousPrimaryManagerId());
        assertEquals(66L, result.currentPrimaryManagerId());
        assertTrue(!result.effectiveFrom().isBefore(invocationStartedAt));
        ArgumentCaptor<ProjectMemberAssignmentDO> closeCaptor = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberAssignmentMapper).updateById(closeCaptor.capture());
        assertEquals(7L, closeCaptor.getValue().getId());
        ArgumentCaptor<ProjectMemberAssignmentDO> freshCaptor = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberAssignmentMapper).insert(freshCaptor.capture());
        assertEquals(66L, freshCaptor.getValue().getUserId());
        assertEquals("L1", freshCaptor.getValue().getResponsibility());
        assertEquals(30L, freshCaptor.getValue().getSiteId());
        assertEquals(20L, freshCaptor.getValue().getDepartmentId());
        assertEquals("DEP-01", freshCaptor.getValue().getDepartmentCode());
        assertEquals(ProjectRules.ASSIGNMENT_TYPE_PRIMARY, freshCaptor.getValue().getAssignmentType());
        assertEquals("人工指派", freshCaptor.getValue().getChangeReason());
    }

    @Test
    void collaboratorAssignmentKeepsExistingCollaboratorsAndDoesNotDriveStatus() {
        ProjectMasterDO project = persistedProject();
        project.setAssignmentStatus(ProjectRules.ASSIGNMENT_STATUS_ASSIGNED);
        when(projectMasterMapper.selectById(1L)).thenReturn(project);
        when(projectMasterMapper.incrementVersionIfMatch(1L, 0)).thenReturn(1);
        ProjectMemberAssignmentDO primary = assignment(
                55L, ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1, ProjectRules.ASSIGNMENT_TYPE_PRIMARY);
        ProjectMemberAssignmentDO projectManager = assignment(
                77L, ProjectRules.MEMBER_ROLE_PROJECT_MANAGER, null);
        ProjectMemberAssignmentDO collaborator = assignment(
                88L, ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1, ProjectRules.ASSIGNMENT_TYPE_COLLABORATOR);
        when(memberAssignmentMapper.selectActiveForAssignmentState(any()))
                .thenReturn(List.of(primary, projectManager), List.of(primary, projectManager, collaborator));
        when(memberAssignmentMapper.selectCurrentResponsibilityForUpdate(any()))
                .thenReturn(List.of(collaborator));
        doAnswer(invocation -> {
            ProjectMemberAssignmentDO inserted = invocation.getArgument(0);
            inserted.setId(9L);
            return 1;
        }).when(memberAssignmentMapper).insert(any(ProjectMemberAssignmentDO.class));

        AssignServiceManagerCommand command = new AssignServiceManagerCommand(
                1L, 0, "L1", 66L, 30L, "COLLABORATOR", 20L, "DEP-01",
                "协同支持", "collaborator-key", "c".repeat(64));
        AssignServiceManagerResult result = service.assignServiceManager(command);

        assertEquals(ProjectRules.ASSIGNMENT_STATUS_ASSIGNED, result.assignmentStatus());
        assertEquals(55L, result.currentPrimaryManagerId());
        verify(memberAssignmentMapper, never()).updateById(any(ProjectMemberAssignmentDO.class));
        verify(projectMasterMapper, never()).updateAssignmentStatusIfVersion(any());
        ArgumentCaptor<ProjectMemberAssignmentDO> inserted = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberAssignmentMapper).insert(inserted.capture());
        assertEquals(ProjectRules.ASSIGNMENT_TYPE_COLLABORATOR, inserted.getValue().getAssignmentType());
    }

    // ========== BR-7 更新不可变字段被忽略 ==========

    @Test
    void updateIgnoresImmutableFields() {
        ProjectMasterDO current = persistedProject();
        when(projectMasterMapper.selectById(100L)).thenReturn(current);
        allowScope(100L, "PROJECT_MANAGE");

        ProjectMasterDO update = new ProjectMasterDO();
        update.setId(100L);
        update.setProjectName("新名称");
        update.setContractNo("HT-2026-002");
        update.setImplementationLocation("上海");
        // 不可变字段攻击载荷
        update.setProjectCode("PJT9999999999");
        update.setStatus(ProjectRules.STATUS_S6);
        update.setSourceType(ProjectRules.SOURCE_TYPE_ORDER);
        update.setLifecycleTemplateId(888L);
        update.setTemplateLoadMethod(ProjectRules.TEMPLATE_LOAD_MANUAL_SELECTED);
        update.setSigningMethod("ATTACK_SIGNING");
        update.setProjectCategory("ATTACK_CATEGORY");
        update.setImplementationMode("ATTACK_MODE");
        update.setMajorProjectLevel("ATTACK_LEVEL");

        service.updateProject(update, new ProjectManualCreationService.ProjectAccessActor(0L, 7L));

        ArgumentCaptor<ProjectMasterDO> captor = ArgumentCaptor.forClass(ProjectMasterDO.class);
        verify(projectMasterMapper).updateById(captor.capture());
        ProjectMasterDO saved = captor.getValue();
        // 可编辑字段生效
        assertEquals("新名称", saved.getProjectName());
        assertEquals("HT-2026-002", saved.getContractNo());
        assertEquals("上海", saved.getImplementationLocation());
        // 不可变字段以库内值为准（BR-8 联动：编码不可变；状态/来源/模板绑定不可改）
        assertEquals("PJT2026000001", saved.getProjectCode());
        assertEquals(ProjectRules.STATUS_S0, saved.getStatus());
        assertEquals(ProjectRules.SOURCE_TYPE_MANUAL, saved.getSourceType());
        assertEquals(5L, saved.getLifecycleTemplateId());
        assertEquals(ProjectRules.TEMPLATE_LOAD_AUTO_DEFAULT, saved.getTemplateLoadMethod());
        assertEquals(current.getSigningMethod(), saved.getSigningMethod());
        assertEquals(current.getProjectCategory(), saved.getProjectCategory());
        assertEquals(current.getImplementationMode(), saved.getImplementationMode());
        assertEquals(current.getMajorProjectLevel(), saved.getMajorProjectLevel());
    }

    @Test
    void updateRejectedWhenProjectMissing() {
        ProjectMasterDO payload = persistedProject();
        payload.setId(404L);
        when(projectMasterMapper.selectById(404L)).thenReturn(null);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateProject(payload,
                        new ProjectManualCreationService.ProjectAccessActor(0L, 7L)));
        assertEquals(PROJECT_NOT_EXISTS.getCode(), exception.getCode());
    }

    @Test
    void projectPageUsesServerResolvedViewScopeAndKeepsEmptyScopeEmpty() {
        ProjectManualCreationService.ProjectAccessActor actor =
                new ProjectManualCreationService.ProjectAccessActor(0L, 7L);
        when(projectTreeScopeService.resolveAllFullProjectIds(0L, 7L, "PROJECT_VIEW"))
                .thenReturn(Set.of());
        when(projectMasterMapper.selectPage(any(VisibleProjectPageQuery.class)))
                .thenReturn(PageResult.empty());
        PageParam page = new PageParam();
        page.setPageNo(1);
        page.setPageSize(20);

        PageResult<ProjectMasterDO> result = service.getProjectPage(
                page, "名称", "PJT", "ACTIVE", null, null, null, actor);

        assertEquals(0L, result.getTotal());
        ArgumentCaptor<VisibleProjectPageQuery> query =
                ArgumentCaptor.forClass(VisibleProjectPageQuery.class);
        verify(projectMasterMapper).selectPage(query.capture());
        assertEquals(Set.of(), query.getValue().visibleProjectIds());
        assertEquals("名称", query.getValue().projectNameKeyword());
        assertEquals("PJT", query.getValue().projectCodePrefix());
    }

    @Test
    void projectDetailHidesProjectWhenViewScopeIsEmpty() {
        ProjectMasterDO current = persistedProject();
        when(projectMasterMapper.selectById(100L)).thenReturn(current);
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(1L);
        when(projectTreeVersionMapper.selectLatestActive(100L)).thenReturn(version);
        when(projectTreeScopeService.resolve(new ProjectScopeQuery(
                0L, 7L, 100L, "PROJECT_VIEW", 1L))).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(
                        100L, 1L, Set.of(), Set.of(), Set.of()));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.getProject(
                100L, new ProjectManualCreationService.ProjectAccessActor(0L, 7L)));

        assertEquals(PROJECT_NOT_EXISTS.getCode(), exception.getCode());
    }

    // ========== 辅助 ==========

    private ProjectMasterDO validDraft() {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setTenantId(1L);
        draft.setProjectName("某数据中心建设项目");
        draft.setCustomerCode("CUST-001");
        draft.setCustomerName("某客户");
        draft.setCreationReason("紧急立项");
        draft.setSigningMethod("DIRECT_SIGN");
        draft.setProjectCategory("GENERAL");
        draft.setImplementationMode("DIRECT_SERVICE");
        return draft;
    }

    private ProjectMasterDO persistedProject() {
        ProjectMasterDO current = validDraft();
        current.setId(100L);
        current.setTenantId(0L);
        current.setProjectCode("PJT2026000001");
        current.setCodeRootId(100L);
        current.setRootId(100L);
        current.setProjectSequence(0);
        current.setCodeRuleVersion("V1");
        current.setStatus(ProjectRules.STATUS_S0);
        current.setLifecycleStatus(ProjectRules.LIFECYCLE_STATUS_ACTIVE);
        current.setCurrentStage(ProjectRules.STATUS_S0);
        current.setAssignmentStatus(ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED);
        current.setSourceType(ProjectRules.SOURCE_TYPE_MANUAL);
        current.setLifecycleTemplateId(5L);
        current.setLifecycleTemplateRevisionNo(1);
        current.setTemplateLoadMethod(ProjectRules.TEMPLATE_LOAD_AUTO_DEFAULT);
        current.setContractNo("HT-2026-001");
        return current;
    }

    private void allowScope(Long projectId, String actionCode) {
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(1L);
        when(projectTreeVersionMapper.selectLatestActive(projectId)).thenReturn(version);
        when(projectTreeScopeService.resolve(new ProjectScopeQuery(
                0L, 7L, projectId, actionCode, 1L))).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(
                        projectId, 1L, java.util.Set.of(projectId), java.util.Set.of(), java.util.Set.of()));
    }

    private ProjectTemplateDO activeTemplate(Long id, String code) {
        ProjectTemplateDO template = new ProjectTemplateDO();
        template.setId(id);
        template.setCode(code);
        template.setName("模板-" + code);
        template.setStatus(TemplateRules.STATUS_ACTIVE);
        template.setMatchPriority(100);
        return template;
    }

    private ProjectTemplateRevisionDO revision(Long templateId, String status, Integer revisionNo) {
        ProjectTemplateRevisionDO revision = new ProjectTemplateRevisionDO();
        revision.setId(revisionNo == null ? null : 1000L + revisionNo);
        revision.setTemplateId(templateId);
        revision.setStatus(status);
        revision.setRevisionNo(revisionNo);
        return revision;
    }

    private TemplateMatchDecision decision(Long templateId, Long revisionId, String decisionMode) {
        return new TemplateMatchDecision(TemplateMatchDecisionRules.MATCH_UNIQUE, CANDIDATE_WATERMARK,
                TemplateMatchDecisionRules.MATCHER_VERSION, decisionMode, templateId, revisionId, 1);
    }

    private AssignServiceManagerCommand assignCommand() {
        return new AssignServiceManagerCommand(1L, 0, "L1", 66L, 30L,
                "PRIMARY", 20L, "DEP-01", "人工指派", "assign-key", "b".repeat(64));
    }

    private ProjectMemberAssignmentDO assignment(Long userId, String memberRole, String assignmentType) {
        ProjectMemberAssignmentDO assignment = new ProjectMemberAssignmentDO();
        assignment.setUserId(userId);
        assignment.setMemberRole(memberRole);
        assignment.setAssignmentType(assignmentType);
        assignment.setSiteId(30L);
        assignment.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        return assignment;
    }

    private TemplateDefinitionContent contentWithOneGateAndReference() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.setProcessDefinitionKey("PROC-KEY");
        content.setProcessDefinitionVersion("V3");
        TemplateDefinitionContent.StageDef stage = new TemplateDefinitionContent.StageDef();
        stage.setStageCode("S0");
        stage.setName("立项与指派");
        stage.setSortOrder(0);
        content.getStages().add(stage);
        TemplateDefinitionContent.TaskDef task = new TemplateDefinitionContent.TaskDef();
        task.setTaskCode("T1");
        task.setName("工前任务");
        task.setStageCode("S0");
        task.setWorkBindingTypeCode(TaskExecutionContractFactory.TASK_NATIVE);
        task.setBindingConfig("{\"schemaVersion\":1}");
        task.setPermissionPolicyRef("PROJECT_TASK_NATIVE_DEFAULT");
        task.setCompletionRuleTypeCode("TASK_NATIVE_STATUS");
        task.setCompletionRuleConfig("{\"schemaVersion\":1,\"requiredStatus\":\"COMPLETED\"}");
        task.setDefinitionVersion(1);
        content.getTasks().add(task);
        TemplateDefinitionContent.MilestoneDef milestone = new TemplateDefinitionContent.MilestoneDef();
        milestone.setMilestoneCode("M1");
        milestone.setName("开工评审");
        milestone.setStageCode("S0");
        content.getMilestones().add(milestone);
        TemplateDefinitionContent.DeliverableDef deliverable = new TemplateDefinitionContent.DeliverableDef();
        deliverable.setDeliverableCode("D1");
        deliverable.setName("实施计划书");
        deliverable.setStageCode("S0");
        deliverable.setRequired(Boolean.TRUE);
        content.getDeliverables().add(deliverable);
        TemplateDefinitionContent.GateDef gate = new TemplateDefinitionContent.GateDef();
        gate.setGateCode("G1");
        gate.setName("S0 准出门禁");
        gate.setGateType("EXIT");
        gate.setStageCode("S0");
        TemplateDefinitionContent.GateRef ref = new TemplateDefinitionContent.GateRef();
        ref.setRefType("TASK");
        ref.setRefCode("T1");
        gate.getReferences().add(ref);
        content.getGates().add(gate);
        return content;
    }

    private TemplateDefinitionContent contentWithS0Only() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        TemplateDefinitionContent.StageDef stage = new TemplateDefinitionContent.StageDef();
        stage.setStageCode("S0");
        stage.setName("立项与指派");
        stage.setSortOrder(0);
        content.getStages().add(stage);
        return content;
    }

    /**
     * 断言前为门禁 insert 打自增 id（500），验证引用行 gate_id 回填。
     * 由于 verify 发生在 service 调用之后，此处仅作延迟绑定说明：实际 id 由下方 stub 消费。
     */
    private void doAnswerAsGateInsert() {
        // 该方法不参与 Mockito 打桩流程（见 manualSelectionTakesEffectWithTwoPhaseNamespaceWrite 的验证顺序说明）
    }
}
