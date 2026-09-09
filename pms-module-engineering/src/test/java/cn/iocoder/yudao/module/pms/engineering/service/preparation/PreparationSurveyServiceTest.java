package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** PRE-02: authorization/CAS and real Spring local transaction rollback without running migrations. */
@ExtendWith(MockitoExtension.class)
class PreparationSurveyServiceTest {
    @Mock PreparationMapper preparationMapper;
    @Mock PreparationSurveyMapper surveyMapper;
    @Mock PreparationQueryService queryService;
    @Mock PreparationItemApplicationService itemService;
    @Mock EngineeringLocationFactService locationService;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi scopeApi;
    @Mock ProjectParticipantFactApi participantApi;
    @Mock OperationAuditApi auditApi;
    PreparationSurveyService service;
    JdbcTemplate jdbc;
    PreparationDO root;
    final PreparationItemApplicationService.Actor actor = new PreparationItemApplicationService.Actor(1L, 7L, "survey-test");

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:survey_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE ast_effects (id INT)");
        jdbc.execute("CREATE TABLE survey_effects (id INT)");
        service = new PreparationSurveyService(preparationMapper, surveyMapper, queryService, itemService,
                locationService, permissionApi, scopeApi, participantApi, auditApi,
                new TransactionTemplate(new DataSourceTransactionManager(ds)));
        root = new PreparationDO(); root.setTenantId(1L); root.setId(2L); root.setProjectId(10L);
        root.setStatusCode("DRAFT"); root.setCurrentMarker(1); root.setVersion(4);
        root.setInputVersion(3); root.setReadinessVersion(2);
    }

    @Test
    void emptyMetadataReturnsRealRootVersionAndNoInventedBusinessValues() {
        PreparationRespVO detail = new PreparationRespVO(); detail.setVersion(4); detail.setAllowedActions(List.of("SUBMIT"));
        when(queryService.getDetail(any(), any())).thenReturn(detail);
        var response = service.get(2L, actor);
        assertEquals(2L, response.getPreparationId()); assertEquals(4, response.getVersion());
        assertNull(response.getSurveyDate()); assertNull(response.getSurveyorUserId());
        assertNull(response.getLocation()); assertNull(response.getAddressId());
        assertEquals(List.of("UPDATE_SURVEY"), response.getAllowedActions());
        verify(surveyMapper, never()).insert(any());
    }

    @Test
    void optionalFieldsAndOmittedLocationPreserveExistingValues() {
        authorize();
        PreparationSurveyDO old = new PreparationSurveyDO(); old.setPreparationId(2L); old.setTenantId(1L);
        old.setSurveyDate(LocalDate.of(2026, 9, 8)); old.setSurveyorUserId(8L);
        old.setLocation("旧地点"); old.setAddressId(31L); old.setAddressVersion(2);
        old.setLocationResolutionStatus("RESOLVED"); old.setGrounding("旧接地");
        when(surveyMapper.selectByPreparation(any())).thenReturn(old);
        when(surveyMapper.update(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        PreparationSurveyPatchReqVO request = request(); request.setGrounding("接地说明");
        var response = service.patch(2L, 4, request, actor);
        assertEquals(5, response.getVersion()); assertEquals(LocalDate.of(2026, 9, 8), response.getSurveyDate());
        assertEquals(31L, response.getAddressId()); assertEquals("接地说明", response.getGrounding());
        assertEquals("旧接地", old.getGrounding());
        verifyNoInteractions(locationService, itemService);
    }

    @Test
    void nonEmptyFallbackSupportsUnresolvedLocationWithoutInventingIds() {
        authorize();
        stubLocation(new EngineeringLocationFactService.LocationFact(null, null, null, null, null, null,
                "UNRESOLVED", null, "{\"fallbackLocation\":\"现场待维护\"}"));
        when(surveyMapper.insert(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        var response = service.patch(2L, 4, locationRequest("现场待维护"), actor);
        assertEquals("UNRESOLVED", response.getLocationResolutionStatus());
        assertEquals("现场待维护", response.getLocation()); assertNull(response.getAddressId());
        assertNull(response.getSiteId()); assertNull(response.getSiteLocationId()); assertEquals(5, response.getVersion());
        verify(locationService).maintain(eq(10L), eq("PREPARATION_SURVEY"), eq(2L), eq(5), eq("现场待维护"), any());
    }

    @Test
    void emptyUnresolvedLocationIsRejectedAndAstWritesRolledBack() {
        authorize();
        stubLocation(new EngineeringLocationFactService.LocationFact(null, null, null, null, null, null,
                "UNRESOLVED", null, "{}"));
        assertThrows(RuntimeException.class, () -> service.patch(2L, 4, locationRequest("  "), actor));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ast_effects", Integer.class));
        verify(surveyMapper, never()).insert(any());
        verify(preparationMapper, never()).invalidateReadinessIfMatch(any());
    }

    @Test
    void resolvedLocationAndSolMetadataCommitInSameTransactionAfterAuthorization() {
        authorize(); stubLocation(resolved());
        when(surveyMapper.insert(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("INSERT INTO survey_effects VALUES (1)"); return 1;
        });
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        var response = service.patch(2L, 4, locationRequest("机房"), actor);
        assertEquals(31L, response.getAddressId()); assertEquals(3, response.getAddressVersion());
        assertEquals(41L, response.getSiteId()); assertEquals(51L, response.getSiteLocationId());
        assertEquals("RESOLVED", response.getLocationResolutionStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ast_effects", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM survey_effects", Integer.class));
        var order = inOrder(participantApi, preparationMapper, locationService, surveyMapper);
        order.verify(participantApi).lockAndRevalidate(any());
        order.verify(preparationMapper).selectForUpdate(any());
        order.verify(locationService).maintain(any(), any(), any(), any(), any(), any());
        order.verify(surveyMapper).insert(any());
    }

    @Test
    void failedRootCasRollsBackBothAstLocationAndSolMetadataWrites() {
        authorize(); stubLocation(resolved());
        when(surveyMapper.insert(any())).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO survey_effects VALUES (1)"); return 1;
        });
        assertThrows(RuntimeException.class, () -> service.patch(2L, 4, locationRequest("机房"), actor));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ast_effects", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM survey_effects", Integer.class));
        verify(auditApi).record(any(), any(), any(), any(), any(), any(), eq("REJECTED"), any());
    }

    @Test
    void staleRootAndFrozenHistoryRejectBeforeCallingAst() {
        authorize();
        assertThrows(RuntimeException.class, () -> service.patch(2L, 3, locationRequest("机房"), actor));
        root.setStatusCode("CONFIRMED");
        assertThrows(RuntimeException.class, () -> service.patch(2L, 4, locationRequest("机房"), actor));
        root.setStatusCode("DRAFT"); root.setCurrentMarker(null);
        assertThrows(RuntimeException.class, () -> service.patch(2L, 4, locationRequest("机房"), actor));
        verifyNoInteractions(locationService, surveyMapper);
    }

    @Test
    void missingManagePermissionRejectsBeforeAnyBusinessReadOrAstEffect() {
        assertThrows(RuntimeException.class, () -> service.patch(2L, 4, locationRequest("机房"), actor));
        verifyNoInteractions(preparationMapper, surveyMapper, locationService);
    }

    @Test
    void managerRoleIsCheckedInsteadOfTrustingOnlyManagePermission() {
        authorizeBeforeRoot();
        when(participantApi.lockAndRevalidate(any())).thenReturn(new ProjectParticipantFact(
                10L, 7L, Set.of("MEMBER"), "PRIMARY", "ACTIVE", "S1", 2, 3L));
        assertThrows(RuntimeException.class, () -> service.patch(2L, 4, locationRequest("机房"), actor));
        verifyNoInteractions(locationService, surveyMapper);
        verify(preparationMapper, never()).selectForUpdate(any());
    }

    @Test
    void suppliedSurveyorUsesExistingCandidateValidationAndFailurePrecedesLocation() {
        authorizeBeforeRoot();
        when(participantApi.lockAndRevalidate(any())).thenReturn(manager());
        doThrow(new IllegalArgumentException("disabled-or-outside-organization"))
                .when(itemService).validateCandidateLocked(10L, 2, 8L);
        PreparationSurveyPatchReqVO request = locationRequest("机房"); request.setSurveyorUserId(8L);
        assertThrows(RuntimeException.class, () -> service.patch(2L, 4, request, actor));
        verifyNoInteractions(locationService, surveyMapper);
        verify(preparationMapper, never()).selectForUpdate(any());
    }

    @Test
    void returnCopyKeepsOriginalMetadataAndLocationHistoryUntouched() {
        PreparationSurveyDO old = new PreparationSurveyDO(); old.setTenantId(1L); old.setPreparationId(2L);
        old.setSurveyDate(LocalDate.of(2026, 9, 8)); old.setGrounding("接地"); old.setLocationSnapshot("{\"history\":1}");
        when(surveyMapper.selectByPreparation(any())).thenReturn(old);
        when(surveyMapper.insert(any())).thenReturn(1);
        service.copy(1L, 2L, 20L, 7L);
        verify(surveyMapper).insert(argThat(row -> row.getPreparationId().equals(20L)
                && "接地".equals(row.getGrounding()) && row.getSurveyDate().equals(old.getSurveyDate())
                && row.getLocationSnapshot().equals(old.getLocationSnapshot())));
        assertEquals(2L, old.getPreparationId()); verify(surveyMapper, never()).update(any());
        verifyNoInteractions(locationService);
    }

    private void authorizeBeforeRoot() {
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(preparationMapper.selectById(any())).thenReturn(root);
        ProjectScopeResult scope = new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of());
        when(scopeApi.resolveCurrent(any())).thenReturn(scope); when(scopeApi.lockAndRevalidate(any())).thenReturn(scope);
    }
    private void authorize() {
        authorizeBeforeRoot(); when(participantApi.lockAndRevalidate(any())).thenReturn(manager());
        when(preparationMapper.selectForUpdate(any())).thenReturn(root);
    }
    private ProjectParticipantFact manager() {
        return new ProjectParticipantFact(10L, 7L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 2, 3L);
    }
    private PreparationSurveyPatchReqVO request() {
        PreparationSurveyPatchReqVO request = new PreparationSurveyPatchReqVO(); request.setExpectedProjectVersion(2); return request;
    }
    private PreparationSurveyPatchReqVO locationRequest(String fallback) {
        PreparationSurveyPatchReqVO request = request();
        request.setLocationCommand(new LocationMaintenanceCommand(999L, null, null, null, fallback, "UNTRUSTED", "999", "999"));
        return request;
    }
    private EngineeringLocationFactService.LocationFact resolved() {
        return new EngineeringLocationFactService.LocationFact(31L, 3, 41L, 4, 51L, 5, "RESOLVED", "{}", "{}");
    }
    private void stubLocation(EngineeringLocationFactService.LocationFact fact) {
        when(locationService.maintain(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("INSERT INTO ast_effects VALUES (1)"); return fact;
        });
    }
}
