package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisQueryServiceTest {

    @Mock RequirementAnalysisRootMapper rootMapper;
    @Mock RequirementAnalysisSectionMapper sectionMapper;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;

    private RequirementAnalysisQueryService service;

    @BeforeEach
    void setUp() {
        service = new RequirementAnalysisQueryService(
                rootMapper, sectionMapper, permissionApi, projectScopeApi, participantFactApi);
    }

    @Test
    void managerWorkspaceAndDraftDetailProjectOnlyServerAuthoritativeActions() {
        stubReader();
        stubManager();
        PreparationDO draft = root(501L, 1, "DRAFT");
        draft.setDraftMarker(1);
        when(rootMapper.selectDraft(any())).thenReturn(draft);
        when(rootMapper.selectById(any())).thenReturn(draft);
        when(sectionMapper.selectList(any())).thenReturn(List.of(section("PROJECT_BACKGROUND", "\"draft\"", "[]")));

        var workspace = service.getWorkspace(100L, actor());
        var detail = service.getDetail(501L, actor());

        assertNull(workspace.getCurrentEffective());
        assertEquals(501L, workspace.getDraft().getPreparationId());
        assertTrue(workspace.getAllowedActions().isEmpty());
        assertEquals(List.of("EDIT", "COMPLETE"), detail.getAllowedActions());
        assertEquals(List.of("EDIT", "ATTACH", "REPLACE", "DETACH"),
                detail.getSections().getFirst().getAllowedActions());
    }

    @Test
    void managerWithoutDraftGetsInitialOrRevisionActionFromCurrentFacts() {
        stubReader();
        stubManager();

        assertEquals(List.of("CREATE_INITIAL_DRAFT"), service.getWorkspace(100L, actor()).getAllowedActions());

        PreparationDO effective = root(501L, 1, "COMPLETED");
        effective.setEffectiveMarker(1);
        when(rootMapper.selectEffective(any())).thenReturn(effective);
        assertEquals(List.of("CREATE_DRAFT"), service.getWorkspace(100L, actor()).getAllowedActions());
        assertTrue(service.getWorkspace(100L, actor()).getCurrentEffective().getAllowedActions().isEmpty());
    }

    @Test
    void historyUsesStableCursorAndNeverProjectsManagerActionsForReadOnlyMember() {
        stubReader();
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(false);
        when(rootMapper.selectCompletedHistory(any())).thenReturn(List.of(
                root(503L, 3, "COMPLETED"), root(502L, 2, "COMPLETED"), root(501L, 1, "COMPLETED")));
        PreparationPageReqVO request = new PreparationPageReqVO();
        request.setPageSize(2);

        var page = service.getHistory(100L, request, actor());

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertEquals("2:502", page.nextCursor());
        assertTrue(page.items().stream().allMatch(item -> item.getAllowedActions().isEmpty()));
    }

    @Test
    void queryMemberReadsCompletedDetailWithoutManagePermission() {
        stubReader();
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE))
                .thenReturn(false);
        PreparationDO completed = root(501L, 1, "COMPLETED");
        when(rootMapper.selectById(any())).thenReturn(completed);
        when(sectionMapper.selectList(any())).thenReturn(List.of(section("PROJECT_BACKGROUND", "\"done\"", "[]")));

        var detail = service.getDetail(501L, actor());

        assertEquals(501L, detail.getPreparationId());
        assertTrue(detail.getAllowedActions().isEmpty());
        assertTrue(detail.getSections().getFirst().getAllowedActions().isEmpty());
    }

    @Test
    void compareReportsAddedRemovedAndContentOrAttachmentChanges() {
        stubReader();
        PreparationDO source = root(501L, 1, "COMPLETED");
        PreparationDO target = root(502L, 2, "COMPLETED");
        when(rootMapper.selectById(any())).thenReturn(source, target);
        when(sectionMapper.selectList(any())).thenReturn(
                List.of(section("A", "\"old\"", "[]"), section("REMOVED", "1", "[]")),
                List.of(section("A", "\"new\"", "[]"), section("ADDED", "1", "[]")));

        var compared = service.compare(501L, 502L, actor());

        assertEquals(List.of("A", "ADDED", "REMOVED"), compared.getSections().stream()
                .map(item -> item.getSectionCode()).toList());
        assertEquals("CHANGED", compared.getSections().getFirst().getChangeType());
        assertTrue(compared.getSections().getFirst().getContentChanged());
        assertFalse(compared.getSections().getFirst().getAttachmentsChanged());
        assertEquals("ADDED", compared.getSections().get(1).getChangeType());
        assertEquals("REMOVED", compared.getSections().get(2).getChangeType());
    }

    @Test
    void draftCanOnlyCompareWithItsSourceCompletedVersion() {
        stubReader();
        PreparationDO draft = root(503L, 3, "DRAFT");
        draft.setSourcePreparationId(501L);
        PreparationDO unrelated = root(502L, 2, "COMPLETED");
        when(rootMapper.selectById(any())).thenReturn(draft, unrelated);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.compare(503L, 502L, actor()));

        assertEquals(cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants
                .REQUIREMENT_ANALYSIS_COMMAND_INVALID.getCode(), error.getCode());
    }

    @Test
    void currentDraftCanCompareWithItsExactSourceCompletedVersion() {
        stubReader();
        stubManager();
        PreparationDO draft = root(503L, 3, "DRAFT");
        draft.setDraftMarker(1);
        draft.setSourcePreparationId(501L);
        PreparationDO source = root(501L, 2, "COMPLETED");
        when(rootMapper.selectById(any())).thenReturn(draft, source);
        when(sectionMapper.selectList(any())).thenReturn(
                List.of(section("A", "\"draft\"", "[]")),
                List.of(section("A", "\"source\"", "[]")));

        var result = service.compare(503L, 501L, actor());

        assertEquals("CHANGED", result.getSections().getFirst().getChangeType());
    }

    @Test
    void missingQueryPermissionFailsBeforeScopeAndSolReads() {
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY))
                .thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getWorkspace(100L, actor()));

        assertEquals(FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(projectScopeApi, participantFactApi, rootMapper, sectionMapper);
    }

    @Test
    void manageOnlySubjectCannotUseReadEndpoints() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getWorkspace(100L, actor()));

        assertEquals(FORBIDDEN.getCode(), error.getCode());
        verify(permissionApi).hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY);
        verifyNoInteractions(projectScopeApi, participantFactApi, rootMapper, sectionMapper);
    }

    private void stubReader() {
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
    }

    private void stubManager() {
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(participantFactApi.inspect(any())).thenReturn(manager());
    }

    private RequirementAnalysisQueryService.Actor actor() {
        return new RequirementAnalysisQueryService.Actor(0L, 9L, "corr-1");
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of());
    }

    private ProjectParticipantFact manager() {
        return new ProjectParticipantFact(100L, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 3, 11L);
    }

    private PreparationDO root(Long id, int version, String status) {
        PreparationDO row = new PreparationDO();
        row.setId(id);
        row.setTenantId(0L);
        row.setProjectId(100L);
        row.setBusinessVersion(version);
        row.setStatusCode(status);
        row.setContentVersion(version);
        row.setVersion(version);
        row.setTemplateId(401L);
        row.setTemplateRevisionId(17L);
        row.setCreateTime(LocalDateTime.of(2026, 8, 27, 10, 0));
        return row;
    }

    private RequirementAnalysisSectionDO section(String code, String value, String files) {
        RequirementAnalysisSectionDO row = new RequirementAnalysisSectionDO();
        row.setId((long) code.hashCode() & Integer.MAX_VALUE);
        row.setTenantId(0L);
        row.setPreparationId(501L);
        row.setSectionCode(code);
        row.setSectionName(code);
        row.setSectionKindCode("CORE");
        row.setFieldTypeCode("RICH_TEXT");
        row.setRequiredFlag(true);
        row.setSortOrder(1);
        row.setSchemaSnapshot("{}");
        row.setValueSnapshot(value);
        row.setAttachmentReferenceSnapshot(files);
        row.setVersion(1);
        return row;
    }
}
