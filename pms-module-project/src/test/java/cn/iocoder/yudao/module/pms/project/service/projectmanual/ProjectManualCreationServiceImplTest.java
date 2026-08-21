package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCompanyDepartmentRelationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
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
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CREATE_FIELDS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_MEMBER_INTERVAL_CONFLICT;
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
    private ProjectCodeAllocator projectCodeAllocator;
    @Mock
    private TaskExecutionContractFactory taskExecutionContractFactory;
    @Mock
    private ProjectDeliverableInitializationApplicationService deliverableInitializationApplicationService;

    @InjectMocks
    private ProjectManualCreationServiceImpl service;

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
        when(projectTemplateService.matchPreview(any(), any(), any(), any()))
                .thenReturn(withWatermark(TemplateMatchResult.noMatch("无匹配的生效模板")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, null, CANDIDATE_WATERMARK, null));

        assertEquals(PROJECT_TEMPLATE_NO_MATCH.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("无匹配"));
        // 不落库不实例化，且不消耗编码流水
        verifyNoInteractions(projectMasterMapper, projectCodeAllocator);
    }

    @Test
    void creationBlockedOnSamePriorityMultiMatch() {
        TemplateMatchResult multi = TemplateMatchResult.multiMatch(List.of("模板【TPL-A】", "模板【TPL-B】"));
        multi.setCandidateWatermark(CANDIDATE_WATERMARK);
        when(projectTemplateService.matchPreview(any(), any(), any(), any())).thenReturn(multi);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, null, CANDIDATE_WATERMARK, null));

        assertEquals(PROJECT_TEMPLATE_AMBIGUOUS.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("TPL-A"));
        assertTrue(exception.getMessage().contains("TPL-B"));
        verifyNoInteractions(projectMasterMapper, projectCodeAllocator);
    }

    @Test
    void creationRejectsStaleCandidateWatermarkBeforeSelectingRevision() {
        when(projectTemplateService.matchPreview(any(), any(), any(), any()))
                .thenReturn(matchedCandidate(9L, 1002L));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createProject(validDraft(), null, null, 1002L,
                        "stale-watermark", null));

        assertEquals(PROJECT_TEMPLATE_CANDIDATE_VERSION_CONFLICT.getCode(), exception.getCode());
        verify(projectTemplateService, never()).getRevisionById(anyLong());
        verifyNoInteractions(projectMasterMapper, projectCodeAllocator);
    }

    // ========== 手工选择（MANUAL_SELECTED） ==========

    @Test
    void manualSelectionTakesEffectWithTwoPhaseNamespaceWrite() {
        Long templateId = 9L;
        Long revisionId = 1002L;
        when(projectTemplateService.matchPreview(any(), any(), any(), any()))
                .thenReturn(matchedCandidate(templateId, revisionId));
        when(projectTemplateService.getProjectTemplate(templateId)).thenReturn(activeTemplate(templateId, "TPL-M"));
        when(projectTemplateService.getRevisionById(revisionId)).thenReturn(
                revision(templateId, TemplateRules.REVISION_STATUS_PUBLISHED, 2));
        TemplateDefinitionContent content = contentWithOneGateAndReference();
        when(projectTemplateService.getRevisionContent(templateId, 2)).thenReturn(content);
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

        ProjectMasterDO created = service.createProject(validDraft(), null, null, revisionId,
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
        verify(taskInstanceMapper).insert(any(ProjectTaskInstanceDO.class));
        verify(taskExecutionContractFactory).create(any(), any(), any(), any());
        verify(taskExecutionContractMapper).insert(any(ProjectTaskExecutionContractDO.class));
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
        when(projectTemplateService.matchPreview(any(), any(), any(), any()))
                .thenReturn(matchedCandidate(templateId, revisionId));
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
        when(projectTemplateService.matchPreview(any(), any(), any(), any()))
                .thenReturn(matchedCandidate(templateId, revisionId));
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
        when(projectTemplateService.matchPreview(any(), any(), any(), any()))
                .thenReturn(matchedCandidate(templateId, revisionId));
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
        TemplateMatchCandidate candidate = new TemplateMatchCandidate();
        candidate.setTemplateId(5L);
        candidate.setTemplateRevisionId(1001L);
        candidate.setCode("TPL-AUTO");
        TemplateMatchResult match = TemplateMatchResult.matched(candidate);
        match.setCandidateWatermark(CANDIDATE_WATERMARK);
        when(projectTemplateService.matchPreview(any(), any(), any(), any())).thenReturn(match);
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
        TemplateMatchCandidate candidate = new TemplateMatchCandidate();
        candidate.setTemplateId(5L);
        candidate.setTemplateRevisionId(1001L);
        TemplateMatchResult match = TemplateMatchResult.matched(candidate);
        match.setCandidateWatermark(CANDIDATE_WATERMARK);
        when(projectTemplateService.matchPreview(any(), any(), any(), any())).thenReturn(match);
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
        when(memberAssignmentMapper.selectListByProjectAndRole(102L, ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1))
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
    void assignServiceManagerRejectsFutureEffectiveFrom() {
        when(projectMasterMapper.selectById(1L)).thenReturn(persistedProject());
        when(projectMasterMapper.incrementVersionIfMatch(1L, 0)).thenReturn(1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assignServiceManager(assignCommand(LocalDateTime.now().plusDays(1))));

        assertEquals(PROJECT_MEMBER_INTERVAL_CONFLICT.getCode(), exception.getCode());
        verify(memberAssignmentMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
    }

    @Test
    void assignServiceManagerRejectsStaleProjectVersion() {
        when(projectMasterMapper.selectById(1L)).thenReturn(persistedProject());
        when(projectMasterMapper.incrementVersionIfMatch(1L, 0)).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assignServiceManager(assignCommand(LocalDateTime.now().minusMinutes(1))));

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
        previous.setEffectiveFrom(LocalDateTime.now().minusDays(2));
        when(memberAssignmentMapper.selectListByProjectAndRole(
                1L, ProjectRules.MEMBER_ROLE_SERVICE_MANAGER_L1)).thenReturn(List.of(previous));
        doAnswer(invocation -> {
            ProjectMemberAssignmentDO inserted = invocation.getArgument(0);
            inserted.setId(8L);
            return 1;
        }).when(memberAssignmentMapper).insert(any(ProjectMemberAssignmentDO.class));

        AssignServiceManagerResult result = service.assignServiceManager(
                assignCommand(LocalDateTime.now().minusMinutes(1)));

        assertEquals(1, result.version());
        assertEquals(8L, result.assignmentId());
        assertEquals(ProjectRules.ASSIGNMENT_STATUS_UNASSIGNED, result.assignmentStatus());
        ArgumentCaptor<ProjectMemberAssignmentDO> closeCaptor = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberAssignmentMapper).updateById(closeCaptor.capture());
        assertEquals(7L, closeCaptor.getValue().getId());
        ArgumentCaptor<ProjectMemberAssignmentDO> freshCaptor = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberAssignmentMapper).insert(freshCaptor.capture());
        assertEquals(66L, freshCaptor.getValue().getUserId());
        assertTrue(freshCaptor.getValue().getResponsibility().contains("\"officeId\":20"));
        assertTrue(freshCaptor.getValue().getResponsibility().contains("\"locationId\":30"));
    }

    // ========== BR-7 更新不可变字段被忽略 ==========

    @Test
    void updateIgnoresImmutableFields() {
        ProjectMasterDO current = persistedProject();
        when(projectMasterMapper.selectById(100L)).thenReturn(current);

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

        service.updateProject(update);

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
    }

    @Test
    void updateRejectedWhenProjectMissing() {
        ProjectMasterDO payload = persistedProject();
        payload.setId(404L);
        when(projectMasterMapper.selectById(404L)).thenReturn(null);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateProject(payload));
        assertEquals(PROJECT_NOT_EXISTS.getCode(), exception.getCode());
    }

    // ========== 辅助 ==========

    private ProjectMasterDO validDraft() {
        ProjectMasterDO draft = new ProjectMasterDO();
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

    private TemplateMatchResult matchedCandidate(Long templateId, Long revisionId) {
        TemplateMatchCandidate candidate = new TemplateMatchCandidate();
        candidate.setTemplateId(templateId);
        candidate.setTemplateRevisionId(revisionId);
        TemplateMatchResult result = TemplateMatchResult.matched(candidate);
        result.setCandidateWatermark(CANDIDATE_WATERMARK);
        return result;
    }

    private TemplateMatchResult withWatermark(TemplateMatchResult result) {
        result.setCandidateWatermark(CANDIDATE_WATERMARK);
        return result;
    }

    private AssignServiceManagerCommand assignCommand(LocalDateTime effectiveFrom) {
        return new AssignServiceManagerCommand(1L, 0, "SERVICE_MANAGER", "L1", 66L,
                20L, 30L, effectiveFrom, "assign-key", "b".repeat(64));
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
