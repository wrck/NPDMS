package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationCandidatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PatchPreparationItemCommand;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreparationItemApplicationServiceTest {

    @Mock private PreparationMapper preparationMapper;
    @Mock private PreparationItemMapper itemMapper;
    @Mock private DynamicFormInstanceMapper formMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private ProjectParticipantFactApi participantFactApi;
    @Mock private ProjectOrganizationFactApi organizationFactApi;
    @Mock private OrganizationScopeApi organizationScopeApi;
    @Mock private FileArtifactApi fileArtifactApi;
    @Mock private OperationAuditApi operationAuditApi;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private PreparationItemApplicationService service;

    @SuppressWarnings("unchecked")
    private void useTransaction() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    void managerAssignsCandidateAndInvalidatesReadiness() {
        useTransaction();
        stubRows(100L);
        when(permissionApi.hasAnyPermissions(100L, PreparationInitializationService.PERMISSION_MANAGE))
                .thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(manager());
        when(organizationFactApi.lockAndRevalidate(any()))
                .thenReturn(new ProjectOrganizationFact(10L, 3, 20L, 30L, "D30"));
        when(organizationScopeApi.hasScope(200L, 20L, 30L)).thenReturn(true);
        when(itemMapper.updateDraftIfMatch(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);

        var response = service.patch(command(Set.of("assignee"), 200L, null, null), actor(100L));

        assertEquals(2, response.getItemVersion());
        assertEquals(1, response.getFormVersion());
        assertEquals(2, response.getPreparationVersion());
        verify(organizationScopeApi).hasScope(200L, 20L, 30L);
        verify(operationAuditApi).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void assigneeSavesValidatedFormAndExactEvidence() {
        useTransaction();
        stubRows(200L);
        when(permissionApi.hasAnyPermissions(200L, PreparationItemApplicationService.PERMISSION_FILL))
                .thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(fileArtifactApi.lockAndRevalidate(any())).thenReturn(new FileArtifactVersionFact(
                800L, 2, "survey-photo", "SITE_SURVEY_EVIDENCE", "evidence.png", 100L,
                "image/png", "sha", "AVAILABLE", "ACTIVE", new FileFactVersion(1, 2, 3), 9L));
        when(itemMapper.updateDraftIfMatch(any())).thenReturn(1);
        when(formMapper.updateDraftIfMatch(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        var evidence = new PatchPreparationItemCommand.EvidenceReference(
                800L, 2, "survey-photo", new FileFactVersion(1, 2, 3), 9L);

        var response = service.patch(command(Set.of("formValueSnapshot", "evidenceReferences"),
                null, "{\"siteCondition\":\"ready\"}", List.of(evidence)), actor(200L));

        assertEquals(2, response.getItemVersion());
        assertEquals(2, response.getFormVersion());
        verify(fileArtifactApi).lockAndRevalidate(any());
        verify(formMapper).updateDraftIfMatch(any());
    }

    @Test
    void candidatesUseTrustedProjectOrganization() {
        when(preparationMapper.selectById(any())).thenReturn(preparation());
        when(permissionApi.hasAnyPermissions(100L, PreparationInitializationService.PERMISSION_MANAGE))
                .thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(participantFactApi.inspect(any())).thenReturn(manager());
        when(organizationFactApi.inspect(any()))
                .thenReturn(new ProjectOrganizationFact(10L, 3, 20L, 30L, "D30"));
        when(organizationScopeApi.pageActiveUsers(any())).thenReturn(new PageResult<>(List.of(), 0L));

        var page = service.getCandidates(1L, new PreparationCandidatePageReqVO(), actor(100L));

        assertEquals(0L, page.getTotal());
        verify(organizationScopeApi).pageActiveUsers(any());
    }

    @Test
    void emptyPatchIsRejectedAndAudited() {
        useTransaction();
        assertThrows(RuntimeException.class, () -> service.patch(command(Set.of(), null, null, null), actor(100L)));
        verify(operationAuditApi).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private void stubRows(Long assigneeId) {
        PreparationDO preparation = preparation();
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectForUpdate(any())).thenReturn(item(assigneeId));
        when(formMapper.selectByItemForUpdate(any())).thenReturn(form());
    }

    private PatchPreparationItemCommand command(Set<String> fields, Long assigneeId,
            String formValue, List<PatchPreparationItemCommand.EvidenceReference> evidence) {
        return new PatchPreparationItemCommand(1L, 2L, 1, 1, 1, 1, 1, 3,
                fields, null, null, assigneeId, null, null, formValue, evidence);
    }

    private PreparationDO preparation() {
        PreparationDO row = new PreparationDO();
        row.setId(1L); row.setProjectId(10L); row.setCurrentMarker(1); row.setStatusCode("DRAFT");
        row.setVersion(1); row.setInputVersion(1); row.setReadinessVersion(1);
        return row;
    }

    private PreparationItemDO item(Long assigneeId) {
        PreparationItemDO row = new PreparationItemDO();
        row.setId(2L); row.setPreparationId(1L); row.setApplicabilityCode("REQUIRED");
        row.setConfirmationStatusCode("PENDING"); row.setAssigneeUserId(assigneeId);
        row.setOutsourced(false); row.setEvidenceReferenceSnapshot("[]"); row.setVersion(1);
        return row;
    }

    private DynamicFormInstanceDO form() {
        DynamicFormInstanceDO row = new DynamicFormInstanceDO();
        row.setId(3L); row.setPreparationId(1L); row.setItemId(2L); row.setStatusCode("DRAFT");
        row.setSchemaSnapshot("{\"schemaVersion\":1,\"formCode\":\"SITE\",\"formVersion\":1,"
                + "\"fields\":[{\"fieldCode\":\"siteCondition\",\"fieldType\":\"TEXT\","
                + "\"required\":true,\"maxLength\":100,\"options\":[],\"sortOrder\":1}]}");
        row.setValueSnapshot("{}"); row.setVersion(1);
        return row;
    }

    private ProjectScopeResult scope() { return new ProjectScopeResult(10L, 9L, Set.of(10L), Set.of()); }
    private ProjectParticipantFact manager() {
        return new ProjectParticipantFact(10L, 100L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 3, 3L);
    }
    private PreparationItemApplicationService.Actor actor(Long id) {
        return new PreparationItemApplicationService.Actor(0L, id, "corr");
    }
}
