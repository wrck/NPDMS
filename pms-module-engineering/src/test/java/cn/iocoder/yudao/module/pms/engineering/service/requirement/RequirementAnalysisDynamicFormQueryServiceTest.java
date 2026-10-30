package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisDynamicFormQueryServiceTest {
    @Mock RequirementAnalysisRootMapper rootMapper;
    @Mock DynamicFormBusinessInstanceApi dynamicFormApi;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    @Mock ProjectWorkBindingFactApi workBindingFactApi;
    @Mock RequirementAnalysisExecutionBinding executionBinding;
    @Mock cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi managementQualifications;

    private static cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Fact qualification(ProjectParticipantFact member) {
        return new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Fact(
                new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Query(0L,member.projectId(),member.userId()),
                cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Source.PROJECT_MEMBER,member,null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void regionalManagerCanReadDraftOnlyWhileRegionalOwnerStillConfirmsQualification(boolean available) {
        var service = new RequirementAnalysisDynamicFormQueryService(rootMapper,dynamicFormApi,
                permissionApi,projectScopeApi,participantFactApi,workBindingFactApi,executionBinding,managementQualifications);
        allowProjectRead();
        when(permissionApi.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(rootMapper.selectById(any())).thenReturn(draft());
        var query = new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Query(0L,100L,9L);
        if (available) {
            when(managementQualifications.inspect(query)).thenReturn(new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Fact(query,
                    cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,null,
                    new cn.iocoder.yudao.module.pms.project.api.participant.ProjectRegionalManagerFactApi.Fact(100L,9L,4L)));
            when(dynamicFormApi.inspectEntityData(any())).thenReturn(form());
            assertEquals(501L,service.getDetail(501L,new RequirementAnalysisDynamicFormQueryService.Actor(0L,9L)).getPreparationId());
        } else {
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.getDetail(501L,new RequirementAnalysisDynamicFormQueryService.Actor(0L,9L)));
            verifyNoInteractions(dynamicFormApi);
        }
        verify(managementQualifications).inspect(query);
    }

    @Test
    void unexpectedParticipantFailureCannotFallBackToRegionalQualification() {
        var service = new RequirementAnalysisDynamicFormQueryService(rootMapper,dynamicFormApi,
                permissionApi,projectScopeApi,participantFactApi,workBindingFactApi,executionBinding,managementQualifications);
        allowProjectRead();
        when(permissionApi.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(rootMapper.selectById(any())).thenReturn(draft());
        when(managementQualifications.inspect(any())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.getDetail(501L,new RequirementAnalysisDynamicFormQueryService.Actor(0L,9L)));
        verifyNoInteractions(participantFactApi,dynamicFormApi);
    }

    @Test
    void administratorWithoutRawPmCanReadWithCurrentUnifiedQualification() {
        var service = new RequirementAnalysisDynamicFormQueryService(rootMapper,dynamicFormApi,
                permissionApi,projectScopeApi,participantFactApi,workBindingFactApi,executionBinding,managementQualifications);
        allowProjectRead();
        when(permissionApi.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(permissionApi.hasAnyRoles(9L,"super_admin")).thenReturn(true);
        when(rootMapper.selectById(any())).thenReturn(draft());
        when(participantFactApi.inspect(any())).thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(
                ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE,"no raw PM"));
        when(managementQualifications.inspect(any())).thenReturn(qualification(new ProjectParticipantFact(
                100L,9L,Set.of("SERVICE_MANAGER"),"PRIMARY","ACTIVE","S1",4L,7L)));
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form());
        assertEquals(501L,service.getDetail(501L,new RequirementAnalysisDynamicFormQueryService.Actor(0L,9L)).getPreparationId());
    }

    @Test
    void initialDraftUsesActiveExecutionRatherThanFixedProjectStage() {
        var service = new RequirementAnalysisDynamicFormQueryService(rootMapper, dynamicFormApi,
                permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
        allowProjectRead();
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(managementQualifications.inspect(any())).thenReturn(qualification(new ProjectParticipantFact(
                100L, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY",
                "ACTIVE", null, 4L, 7L)));
        when(workBindingFactApi.inspect(any())).thenReturn(binding());
        when(executionBinding.canCreate(binding())).thenReturn(true);
        var actor = new RequirementAnalysisDynamicFormQueryService.Actor(0L, 9L);
        assertEquals(List.of("CREATE_INITIAL_DRAFT"), service.getWorkspace(100L, actor).getAllowedActions());
        when(executionBinding.canCreate(binding())).thenReturn(false);
        assertTrue(service.getWorkspace(100L, actor).getAllowedActions().isEmpty());
        when(managementQualifications.inspect(any())).thenReturn(null);
        assertTrue(service.getWorkspace(100L, actor).getAllowedActions().isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PROJECT_MANAGER","SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2"})
    void draftProjectsFrozenDynamicFormAndServerAuthoritativeActions(String role) {
        RequirementAnalysisDynamicFormQueryService service = new RequirementAnalysisDynamicFormQueryService(
                rootMapper, dynamicFormApi, permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
        PreparationDO root = draft();
        when(rootMapper.selectById(any())).thenReturn(root);
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY,
                RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(
                100L, 2L, Set.of(100L), Set.of()));
        when(managementQualifications.inspect(any())).thenReturn(qualification(new ProjectParticipantFact(
                100L, 9L, Set.of(role), "PRIMARY",
                "ACTIVE", "S1", 4L, 7L)));
        when(executionBinding.currentBinding(root)).thenReturn(binding());
        when(executionBinding.canWrite(root)).thenReturn(true);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form());

        var detail = service.getDetail(501L, new RequirementAnalysisDynamicFormQueryService.Actor(0L, 9L));

        assertEquals(9001L, detail.getDynamicFormInstanceId());
        assertEquals(7, detail.getDynamicFormInstanceVersion());
        assertEquals(false, detail.getValues().get("requiresCutover"));
        assertEquals(0, detail.getValues().get("machineCount"));
        assertEquals(List.of("PATCH_FORM", "COMPLETE"), detail.getAllowedActions());
        assertTrue(detail.getCompletionBlockers().isEmpty());
        verify(workBindingFactApi,never()).inspect(any());
        assertEquals(0, detail.getSections().size());
    }

    @Test
    void explicitStageUsesOnlyItsBindingAndCannotBorrowAnAvailableTask() {
        var service = new RequirementAnalysisDynamicFormQueryService(rootMapper,dynamicFormApi,
                permissionApi,projectScopeApi,participantFactApi,workBindingFactApi,executionBinding,managementQualifications);
        allowProjectRead();
        when(permissionApi.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(managementQualifications.inspect(any())).thenReturn(qualification(new ProjectParticipantFact(
                100L,9L,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),"PRIMARY","ACTIVE",null,4L,7L)));
        var json = (tools.jackson.databind.node.ObjectNode) cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(binding()));
        json.putNull("projectTaskId"); json.putNull("projectTaskVersion"); json.put("projectStageId",600L); json.put("projectStageVersion",1);
        var stage = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(json.toString(),ProjectWorkBindingFact.class);
        when(workBindingFactApi.inspectStage(any())).thenReturn(stage);
        when(executionBinding.canCreate(stage)).thenReturn(true);
        when(executionBinding.observeCurrent(stage)).thenReturn(new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection(
                null,new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext(100L,4L,600L,1,701L,1,800L,900L,1,1,true)));
        var actor = new RequirementAnalysisDynamicFormQueryService.Actor(0L,9L);
        assertEquals(List.of("CREATE_INITIAL_DRAFT"),service.getWorkspace(100L,actor,600L,null).getAllowedActions());
        verify(workBindingFactApi).inspectStage(argThat(query -> query.projectId()==100L && query.projectStageId()==600L));
        when(workBindingFactApi.inspectStage(any())).thenThrow(new IllegalStateException("stage unavailable"));
        assertTrue(service.getWorkspace(100L,actor,600L,null).getAllowedActions().isEmpty());
        verify(workBindingFactApi,never()).inspect(any()); verify(workBindingFactApi,never()).inspectTask(any());
    }

    @Test
    void explicitTaskDoesNotFallBackToDefaultOrStageBinding() {
        var service = new RequirementAnalysisDynamicFormQueryService(rootMapper,dynamicFormApi,
                permissionApi,projectScopeApi,participantFactApi,workBindingFactApi,executionBinding,managementQualifications);
        allowProjectRead();
        when(permissionApi.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(managementQualifications.inspect(any())).thenReturn(qualification(new ProjectParticipantFact(
                100L,9L,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),"PRIMARY","ACTIVE",null,4L,7L)));
        when(workBindingFactApi.inspectTask(any())).thenReturn(binding());
        when(executionBinding.canCreate(any())).thenReturn(true);
        when(executionBinding.observeCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection(
                new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(100L,4L,200L,1,701L,1,800L,900L,1,1,600L,1,true,null),null));
        var actor = new RequirementAnalysisDynamicFormQueryService.Actor(0L,9L);

        assertEquals(List.of("CREATE_INITIAL_DRAFT"),service.getWorkspace(100L,actor,null,200L).getAllowedActions());
        verify(workBindingFactApi).inspectTask(argThat(query -> query.projectId()==100L && query.projectTaskId()==200L));
        when(workBindingFactApi.inspectTask(any())).thenThrow(new IllegalStateException("task unavailable"));
        assertTrue(service.getWorkspace(100L,actor,null,200L).getAllowedActions().isEmpty());
        assertThrows(RuntimeException.class, () -> service.getWorkspace(100L,actor,600L,200L));
        verify(workBindingFactApi,never()).inspect(any()); verify(workBindingFactApi,never()).inspectStage(any());
    }

    @Test
    void sharedDraftActionsUseTheSelectedNodeRatherThanItsInactiveOrigin() {
        var service = new RequirementAnalysisDynamicFormQueryService(rootMapper,dynamicFormApi,
                permissionApi,projectScopeApi,participantFactApi,workBindingFactApi,executionBinding,managementQualifications);
        allowProjectRead();
        when(permissionApi.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(managementQualifications.inspect(any())).thenReturn(qualification(new ProjectParticipantFact(
                100L,9L,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),"PRIMARY","ACTIVE",null,4L,7L)));
        var root = draft();
        when(rootMapper.selectDraft(any())).thenReturn(root);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form());
        var binding = binding();
        var selected = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection(
                new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(100L,4L,700L,1,701L,1,800L,900L,1,1,600L,1,true,null),null);
        when(workBindingFactApi.inspectTask(any())).thenReturn(binding);
        when(executionBinding.observeCurrent(binding)).thenReturn(selected);
        when(executionBinding.currentBinding(root,selected)).thenReturn(binding);
        when(executionBinding.canWrite(root,selected)).thenReturn(true);
        var actor = new RequirementAnalysisDynamicFormQueryService.Actor(0L,9L);

        assertEquals(List.of("PATCH_FORM","COMPLETE"),service.getWorkspace(100L,actor,null,700L).getDraft().getAllowedActions());
        verify(executionBinding,never()).canWrite(root);
        when(workBindingFactApi.inspectTask(any())).thenThrow(new IllegalStateException("selected task unavailable"));
        assertTrue(service.getWorkspace(100L,actor,null,700L).getDraft().getAllowedActions().isEmpty());
    }

    @Test
    void inactiveExecutionDoesNotProjectAnyWriteAction() {
        RequirementAnalysisDynamicFormQueryService service = new RequirementAnalysisDynamicFormQueryService(
                rootMapper, dynamicFormApi, permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
        PreparationDO root = draft();
        when(rootMapper.selectById(any())).thenReturn(root);
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY,
                RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(
                100L, 2L, Set.of(100L), Set.of()));
        when(managementQualifications.inspect(any())).thenReturn(qualification(new ProjectParticipantFact(
                100L, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY",
                "ACTIVE", "S2", 4L, 7L)));
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form());

        var detail = service.getDetail(501L, new RequirementAnalysisDynamicFormQueryService.Actor(0L, 9L));

        assertEquals(List.of(), detail.getAllowedActions());
        assertTrue(detail.getCompletionBlockers().isEmpty());
    }

    @Test
    void draftCanOnlyCompareWithItsSourceCompletedVersion() {
        RequirementAnalysisDynamicFormQueryService service = new RequirementAnalysisDynamicFormQueryService(
                rootMapper, dynamicFormApi, permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
        PreparationDO draft = draft();
        draft.setSourcePreparationId(401L);
        PreparationDO unrelatedCompleted = completed(400L);
        when(rootMapper.selectById(any())).thenReturn(draft, unrelatedCompleted);
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY,
                RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(
                100L, 2L, Set.of(100L), Set.of()));

        assertThrows(RuntimeException.class,
                () -> service.compare(501L, 400L,
                        new RequirementAnalysisDynamicFormQueryService.Actor(0L, 9L)));
    }

    @Test
    void detailProjectsControlledFilesByPlainFieldKey() {
        RequirementAnalysisDynamicFormQueryService service = new RequirementAnalysisDynamicFormQueryService(
                rootMapper, dynamicFormApi, permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
        PreparationDO root = completed(501L);
        when(rootMapper.selectById(any())).thenReturn(root);
        allowProjectRead();
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(formWithFile(501L, true));

        var detail = service.getDetail(501L, new RequirementAnalysisDynamicFormQueryService.Actor(0L, 9L));

        assertTrue(detail.getControlledFiles().containsKey("surveyAttachments"));
        assertFalse(detail.getControlledFiles().containsKey("FORM_FIELD_ATTACHMENT/surveyAttachments"));
    }

    @Test
    void compareReportsFileOnlyChange() {
        RequirementAnalysisDynamicFormQueryService service = new RequirementAnalysisDynamicFormQueryService(
                rootMapper, dynamicFormApi, permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
        PreparationDO source = completed(401L);
        PreparationDO target = completed(402L);
        target.setBusinessVersion(2);
        when(rootMapper.selectById(any())).thenReturn(source, target);
        allowProjectRead();
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(
                formWithFile(401L, true), formWithFile(402L, false));

        var result = service.compare(401L, 402L,
                new RequirementAnalysisDynamicFormQueryService.Actor(0L, 9L));

        var difference = result.getFields().getFirst();
        assertEquals("surveyAttachments", difference.getFieldKey());
        assertEquals("CHANGED", difference.getChangeType());
        assertTrue(difference.getControlledFilesChanged());
    }

    @Test
    void historyProjectsSolSummariesWithoutPerRowDynamicFormOrParticipantReads() {
        RequirementAnalysisDynamicFormQueryService service = new RequirementAnalysisDynamicFormQueryService(
                rootMapper, dynamicFormApi, permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
        List<PreparationDO> history = IntStream.rangeClosed(1, 100).mapToObj(index -> {
            PreparationDO row = completed(500L + index);
            row.setBusinessVersion(101 - index);
            row.setDynamicFormInstanceId(9000L + index);
            return row;
        }).toList();
        when(rootMapper.selectCompletedHistory(any())).thenReturn(history);
        allowProjectRead();
        var request = new cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO();
        request.setPageSize(100);

        var result = service.getHistory(100L, request,
                new RequirementAnalysisDynamicFormQueryService.Actor(0L, 9L));

        assertEquals(100, result.items().size());
        assertEquals(9001L, result.items().getFirst().getDynamicFormInstanceId());
        assertNull(result.items().getFirst().getDynamicFormInstanceVersion());
        verifyNoInteractions(dynamicFormApi, participantFactApi, workBindingFactApi, executionBinding, managementQualifications);
    }

    private void allowProjectRead() {
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY,
                RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(
                100L, 2L, Set.of(100L), Set.of()));
    }

    private PreparationDO draft() {
        PreparationDO root = new PreparationDO();
        root.setId(501L);
        root.setTenantId(0L);
        root.setProjectId(100L);
        root.setBusinessVersion(1);
        root.setStatusCode("DRAFT");
        root.setDraftMarker(1);
        root.setContentVersion(2);
        root.setVersion(3);
        root.setTemplateId(702L);
        root.setTemplateRevisionId(703L);
        root.setDynamicFormInstanceId(9001L);
        root.setEntityValueJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(form().ordinaryValues()));
        return root;
    }

    private PreparationDO completed(long id) {
        PreparationDO root = draft();
        root.setId(id);
        root.setBusinessVersion(1);
        root.setStatusCode("COMPLETED");
        root.setDraftMarker(null);
        root.setEffectiveMarker(null);
        return root;
    }

    private DynamicFormInstanceFact form() {
        DynamicFormProviderKey provider = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
        return new DynamicFormInstanceFact(0L, provider,
                new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", "501"), 9001L,
                10L, 11L, 2, 5, "FORM_CREATE_ELEMENT_PLUS", "3.4.0", "3.2.38",
                "{}", "[]", List.of(
                new DynamicFormFieldDescriptor("requiresCutover", "switch", false, false,
                        "BOOLEAN", null, null, null, List.of()),
                new DynamicFormFieldDescriptor("machineCount", "inputNumber", false, false,
                        "NUMBER", null, null, null, List.of())),
                Map.of("requiresCutover", false, "machineCount", 0),
                new DynamicFormValidationFact("VALID", List.of()), List.of(), 7,
                DynamicFormBusinessAction.READ,
                new DynamicFormPolicyFact(DynamicFormBusinessAction.READ, true, null, 3L, "DRAFT"));
    }

    private DynamicFormInstanceFact formWithFile(long preparationId, boolean populated) {
        DynamicFormProviderKey provider = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
        FileReferenceSetKey setKey = new FileReferenceSetKey("PLATFORM", "DYNAMIC_FORM_INSTANCE", "9001",
                "FORM_FIELD_ATTACHMENT/surveyAttachments");
        List<FileArtifactVersionFact> files = populated ? List.of(new FileArtifactVersionFact(
                801L, 1, "survey-slot", "DYNAMIC_FORM_ATTACHMENT", "survey.pdf", 10L,
                "application/pdf", null, "AVAILABLE", "ACTIVE", new FileFactVersion(1, 1, 1), 7L)) : List.of();
        return new DynamicFormInstanceFact(0L, provider,
                new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", String.valueOf(preparationId)), 9001L,
                10L, 11L, 2, 5, "FORM_CREATE_ELEMENT_PLUS", "3.4.0", "3.2.38",
                "{}", "[]", List.of(new DynamicFormFieldDescriptor(
                "surveyAttachments", "PmsFileArtifact", true, false, "FILE", null, null, null, List.of())),
                Map.of(), new DynamicFormValidationFact("VALID", List.of()),
                List.of(new FileReferenceSetFact(setKey, 7L, files)), 7,
                DynamicFormBusinessAction.READ,
                new DynamicFormPolicyFact(DynamicFormBusinessAction.READ, true, null, 3L, "COMPLETED"));
    }

    private ProjectWorkBindingFact binding() {
        return new ProjectWorkBindingFact(100L, 4L, 700L, 1, 701L, 1,
                702L, 2, "BUSINESS_OBJECT", "SOL", "REQUIREMENT_ANALYSIS",
                "PRE_04_REQUIREMENT_ANALYSIS", null, null, null, null,
                703L, 1, "{}", 10L, 11L, 2, 5);
    }
}
