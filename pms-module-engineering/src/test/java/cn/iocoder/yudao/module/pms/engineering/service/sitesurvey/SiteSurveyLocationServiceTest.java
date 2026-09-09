package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyRowQuery;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** FR-ENG-001：原现场工勘安全写入，不以Preparation替代原实体。 */
@ExtendWith(MockitoExtension.class)
class SiteSurveyLocationServiceTest {
    @Mock private SiteSurveyMapper mapper;
    @Mock private EngineeringLocationFactService locationFactService;
    @Mock private ProjectScopeApi scopeApi;
    private SiteSurveyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SiteSurveyServiceImpl();
        ReflectionTestUtils.setField(service, "siteSurveyMapper", mapper);
        ReflectionTestUtils.setField(service, "locationFactService", locationFactService);
        ReflectionTestUtils.setField(service, "projectScopeApi", scopeApi);
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        lenient().when(scopeApi.resolveCurrent(any())).thenReturn(scope(4L));
        lenient().when(scopeApi.lockAndRevalidate(any())).thenReturn(scope(4L));
    }

    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, -1})
    void createAlwaysInitializesDraftAndVersionZero(int suppliedStatus) {
        stubInsert();
        var request = request(); request.setStatus(suppliedStatus); request.setVersion(99);
        assertEquals(101L, service.createSiteSurvey(request));
        verify(mapper).insert(argThat((SiteSurveyDO row) -> row.getStatus() == 0 && row.getVersion() == 0
                && row.getTenantId() == 1L && "7".equals(row.getCreator()) && row.getId() == 101L));
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
        verifyNoInteractions(locationFactService);
    }

    @Test void createRejectsClientPrimaryKey() {
        var request = request(); request.setId(88L);
        error(BAD_REQUEST, () -> service.createSiteSurvey(request));
        verifyNoInteractions(mapper, locationFactService, scopeApi);
    }

    @Test void createRejectsDuplicateCodeWithoutLocationWrite() {
        when(mapper.selectByProjectIdAndCode(10L, "SUR-1")).thenReturn(existing(0));
        error(SITE_SURVEY_CODE_DUPLICATE, () -> service.createSiteSurvey(request()));
        verify(mapper, never()).insert(any(SiteSurveyDO.class)); verifyNoInteractions(locationFactService);
    }

    @Test void createsStructuredLocationAtVersionZeroUsingTrustedProject() {
        stubInsert();
        when(mapper.initializeLocationIfMatch(any(), any())).thenReturn(1);
        when(locationFactService.maintain(eq(10L), eq("SITE_SURVEY"), eq(101L), eq(0), eq("核心机房"), any()))
                .thenReturn(resolved());
        var request = request(); request.setLocationMaintenance(command());
        service.createSiteSurvey(request);
        verify(mapper).initializeLocationIfMatch(argThat(c -> c.expectedVersion() == 0 && c.expectedStatus() == 0),
                argThat(row -> row.getSiteLocationId() == 31L && row.getVersion() == 0));
        verify(scopeApi).resolveCurrent(argThat(q -> q.tenantId() == 1L && q.subjectUserId() == 7L
                && q.anchorProjectId() == 10L && ProjectScopeApi.ACTION_MANAGE.equals(q.actionCode())));
    }

    @Test void requiresFallbackBeforeInsertingWhenNoCommand() {
        var request = request(); request.setLocation(" ");
        error(SITE_SURVEY_LOCATION_REQUIRED, () -> service.createSiteSurvey(request));
        verify(mapper, never()).insert(any(SiteSurveyDO.class)); verifyNoInteractions(locationFactService);
    }

    @Test void acceptsLegalUnresolvedCommandFallbackAndClearsAllStructuredFields() {
        lock(existing(0)); when(mapper.updateDraftIfMatch(any(), any())).thenReturn(1);
        when(locationFactService.maintain(anyLong(), anyString(), anyLong(), anyInt(), anyString(), any()))
                .thenReturn(new EngineeringLocationFactService.LocationFact(11L, 1, 21L, 2, 31L, 3,
                        "UNRESOLVED", "stale-address", "stale-location"));
        var request = updateRequest(); request.setLocation("待维护的新机房"); request.setLocationMaintenance(command());
        service.updateSiteSurvey(request);
        verify(mapper).updateDraftIfMatch(any(), argThat(row -> {
            assertUnresolved(row); return "待维护的新机房".equals(row.getLocation());
        }));
    }

    @Test void rejectsUnresolvedCommandWithoutFallback() {
        stubInsert();
        when(locationFactService.maintain(anyLong(), anyString(), anyLong(), anyInt(), isNull(), any()))
                .thenReturn(new EngineeringLocationFactService.LocationFact(null, null, null, null, null, null,
                        "UNRESOLVED", null, null));
        var request = request(); request.setLocation(null); request.setLocationMaintenance(command());
        error(SITE_SURVEY_LOCATION_REQUIRED, () -> service.createSiteSurvey(request));
        verify(mapper, never()).initializeLocationIfMatch(any(), any());
    }

    @Test void rejectsInvalidLocationResult() {
        stubInsert();
        var request = request(); request.setLocationMaintenance(command());
        error(SITE_SURVEY_LOCATION_INVALID, () -> service.createSiteSurvey(request));
        verify(mapper, never()).initializeLocationIfMatch(any(), any());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3})
    void frozenHistoryRejectsUpdateAndDeleteBeforeAst(int state) {
        lock(existing(state));
        var request = updateRequest(); request.setLocationMaintenance(command());
        error(SITE_SURVEY_STATUS_INVALID, () -> service.updateSiteSurvey(request));
        error(SITE_SURVEY_STATUS_INVALID, () -> service.deleteSiteSurvey(101L, 4));
        verify(mapper, never()).updateDraftIfMatch(any(), any());
        verify(mapper, never()).deleteDraftIfMatch(any()); verifyNoInteractions(locationFactService);
    }

    @Test void rejectedCanSaveWithoutImplicitStateChangeButCannotDeleteOrConfirm() {
        lock(existing(2)); when(mapper.updateDraftIfMatch(any(), any())).thenReturn(1);
        service.updateSiteSurvey(updateRequest());
        verify(mapper).updateDraftIfMatch(argThat(c -> c.expectedStatus() == 2), argThat(row -> row.getStatus() == 2));
        error(SITE_SURVEY_STATUS_INVALID, () -> service.deleteSiteSurvey(101L, 4));
        error(SITE_SURVEY_STATUS_INVALID, () -> service.confirmSiteSurvey(101L, 4));
    }

    @Test void updateRejectsMissingIdentityAndVersionBeforeQuery() {
        var request = updateRequest(); request.setId(null);
        error(BAD_REQUEST, () -> service.updateSiteSurvey(request));
        request.setId(101L); request.setVersion(null);
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.updateSiteSurvey(request));
        verifyNoInteractions(mapper, scopeApi, locationFactService);
    }

    @Test void rejectsProjectAndCodeTamperingOrLifecycleFromUpdate() {
        lock(existing(0)); var request = updateRequest(); request.setProjectId(20L);
        error(BAD_REQUEST, () -> service.updateSiteSurvey(request));
        request.setProjectId(10L); request.setCode("HACK");
        error(BAD_REQUEST, () -> service.updateSiteSurvey(request));
        request.setCode("SUR-1"); request.setStatus(3);
        error(SITE_SURVEY_STATUS_INVALID, () -> service.updateSiteSurvey(request));
        verify(mapper, never()).updateDraftIfMatch(any(), any()); verifyNoInteractions(locationFactService);
    }

    @Test void rejectsPrimaryKeySubstitutionIntoAnotherProject() {
        var row = existing(0); row.setId(202L); row.setProjectId(20L);
        when(mapper.selectByRow(new SiteSurveyRowQuery(1L, 202L))).thenReturn(row);
        var request = updateRequest(); request.setId(202L);
        error(FORBIDDEN, () -> service.updateSiteSurvey(request));
        verify(mapper, never()).selectForUpdate(any()); verifyNoInteractions(locationFactService);
    }

    @Test void everyCommandRequiresVersionWithoutAnyOldOverload() {
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.deleteSiteSurvey(101L, null));
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.confirmSiteSurvey(101L, null));
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.rejectSiteSurvey(101L, null));
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.archiveSiteSurvey(101L, null));
        for (String name : new String[]{"deleteSiteSurvey", "confirmSiteSurvey", "rejectSiteSurvey", "archiveSiteSurvey"}) {
            assertThrows(NoSuchMethodException.class, () -> SiteSurveyService.class.getMethod(name, Long.class));
        }
        verifyNoInteractions(mapper, scopeApi, locationFactService);
    }

    @Test void staleVersionRejectsBeforeLocationWrite() {
        lock(existing(0)); var request = updateRequest(); request.setVersion(3); request.setLocationMaintenance(command());
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.updateSiteSurvey(request));
        verify(mapper, never()).updateDraftIfMatch(any(), any()); verifyNoInteractions(locationFactService);
    }

    @ParameterizedTest @ValueSource(ints = {0, 2})
    void casMustAffectExactlyOneRowForEveryWrite(int affected) {
        lock(existing(0));
        when(mapper.updateDraftIfMatch(any(), any())).thenReturn(affected);
        when(mapper.deleteDraftIfMatch(any())).thenReturn(affected);
        when(mapper.updateStatusIfMatch(any(), anyInt())).thenReturn(affected);
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.updateSiteSurvey(updateRequest()));
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.deleteSiteSurvey(101L, 4));
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.confirmSiteSurvey(101L, 4));
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.rejectSiteSurvey(101L, 4));
        lock(existing(1));
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.archiveSiteSurvey(101L, 4));
    }

    @Test void projectScopeDeniesCreateAndAllExistingWrites() {
        when(scopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L, 4L, Set.of(), Set.of(10L)));
        when(mapper.selectByRow(any())).thenReturn(existing(0));
        error(FORBIDDEN, () -> service.createSiteSurvey(request()));
        error(FORBIDDEN, () -> service.updateSiteSurvey(updateRequest()));
        error(FORBIDDEN, () -> service.deleteSiteSurvey(101L, 4));
        error(FORBIDDEN, () -> service.confirmSiteSurvey(101L, 4));
        error(FORBIDDEN, () -> service.rejectSiteSurvey(101L, 4));
        error(FORBIDDEN, () -> service.archiveSiteSurvey(101L, 4));
        verify(mapper, never()).selectForUpdate(any()); verifyNoInteractions(locationFactService);
    }

    @Test void changedOrRevokedScopeFailsClosed() {
        when(scopeApi.lockAndRevalidate(any())).thenReturn(scope(5L));
        error(FORBIDDEN, () -> service.createSiteSurvey(request()));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L, 4L, Set.of(), Set.of()));
        error(FORBIDDEN, () -> service.createSiteSurvey(request()));
        verifyNoInteractions(mapper, locationFactService);
    }

    @Test void absentTrustedTenantOrActorNeverWrites() {
        TenantContextHolder.clear();
        error(FORBIDDEN, () -> service.createSiteSurvey(request()));
        TenantContextHolder.setTenantId(1L); SecurityContextHolder.clearContext();
        error(FORBIDDEN, () -> service.createSiteSurvey(request()));
        verifyNoInteractions(mapper, scopeApi, locationFactService);
    }

    @Test void unchangedOrOmittedLocationKeepsStructuredIdsAndNeverCallsAst() {
        var old = existing(0); lock(old); when(mapper.updateDraftIfMatch(any(), any())).thenReturn(1);
        var request = updateRequest(); service.updateSiteSurvey(request);
        request.setLocation(null); service.updateSiteSurvey(request);
        verify(mapper, times(2)).updateDraftIfMatch(any(), argThat(row -> row.getSiteLocationId() == 31L
                && row.getAddressId() == 11L && row.getSiteId() == 21L && "RESOLVED".equals(row.getLocationResolutionStatus())
                && "location-snapshot".equals(row.getLocationSnapshot()) && "核心机房".equals(row.getLocation())));
        verifyNoInteractions(locationFactService);
    }

    @Test void fallbackChangeClearsOldIdsVersionsAndSnapshots() {
        lock(existing(0)); when(mapper.updateDraftIfMatch(any(), any())).thenReturn(1);
        var request = updateRequest(); request.setLocation("新地点待维护"); service.updateSiteSurvey(request);
        verify(mapper).updateDraftIfMatch(any(), argThat(row -> {
            assertUnresolved(row); return "新地点待维护".equals(row.getLocation());
        }));
        verifyNoInteractions(locationFactService);
    }

    @Test void locationCommandUsesLockedProjectAndNextVersionOnlyAfterScopeAndRowLocks() {
        lock(existing(0)); when(mapper.updateDraftIfMatch(any(), any())).thenReturn(1);
        when(locationFactService.maintain(eq(10L), eq("SITE_SURVEY"), eq(101L), eq(5), eq("核心机房"), any()))
                .thenReturn(resolved());
        var request = updateRequest(); request.setLocationMaintenance(command()); service.updateSiteSurvey(request);
        var order = inOrder(scopeApi, mapper, locationFactService);
        order.verify(mapper).selectByRow(any()); order.verify(scopeApi).resolveCurrent(any());
        order.verify(scopeApi).lockAndRevalidate(any()); order.verify(mapper).selectForUpdate(any());
        order.verify(locationFactService).maintain(eq(10L), anyString(), eq(101L), eq(5), anyString(), any());
        order.verify(mapper).updateDraftIfMatch(any(), any());
    }

    @Test void allOriginalBusinessFieldsSurviveCreateAndUpdate() {
        stubInsert(); lock(existing(0)); when(mapper.updateDraftIfMatch(any(), any())).thenReturn(1);
        var request = request(); fillOriginalFields(request); service.createSiteSurvey(request);
        verify(mapper).insert(argThat((SiteSurveyDO row) -> assertOriginalFields(row)));
        request.setId(101L); request.setVersion(4); service.updateSiteSurvey(request);
        verify(mapper).updateDraftIfMatch(any(), argThat(this::assertOriginalFields));
    }

    @Test void validTransitionsAndDeleteNeverRewriteLocationOrGenericCrud() {
        lock(existing(0)); when(mapper.updateStatusIfMatch(any(), anyInt())).thenReturn(1);
        when(mapper.deleteDraftIfMatch(any())).thenReturn(1);
        service.confirmSiteSurvey(101L, 4); service.rejectSiteSurvey(101L, 4); service.deleteSiteSurvey(101L, 4);
        lock(existing(1)); service.archiveSiteSurvey(101L, 4);
        verify(mapper).updateStatusIfMatch(argThat(c -> c.expectedStatus() == 1 && c.expectedVersion() == 4), eq(3));
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
        verify(mapper, never()).updateDraftIfMatch(any(), any()); verifyNoInteractions(locationFactService);
    }

    @Test void legacyHistoryReadsRemainAvailableWithoutTreatingOldProjectAsModernScope() {
        var legacy = existing(3); legacy.setProjectId(999L);
        when(mapper.selectById(101L)).thenReturn(legacy);
        assertSame(legacy, service.getSiteSurvey(101L));
        var request = new cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo.SiteSurveyPageReqVO();
        request.setProjectId(999L); request.setStatus(3); request.setCode("HISTORY");
        request.setName("旧工勘"); request.setSurveyorUserId(7L); request.setPageNo(2); request.setPageSize(10);
        service.getSiteSurveyPage(request);
        verify(mapper).selectPage(argThat((cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyPageQuery query) ->
                query.getProjectId() == 999L && query.getStatus() == 3 && "HISTORY".equals(query.getCode())
                && "旧工勘".equals(query.getName()) && query.getSurveyorUserId() == 7L
                && query.getPageNo() == 2 && query.getPageSize() == 10));
        verifyNoInteractions(scopeApi, locationFactService);
    }

    @Test void rowIdentityChangingBetweenLocateAndLockRejectsBeforeAst() {
        when(mapper.selectByRow(any())).thenReturn(existing(0));
        var changed = existing(0); changed.setProjectId(20L);
        when(mapper.selectForUpdate(any())).thenReturn(changed);
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.updateSiteSurvey(updateRequest()));
        changed.setProjectId(10L); changed.setTenantId(2L);
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.updateSiteSurvey(updateRequest()));
        changed.setTenantId(1L); changed.setId(202L);
        error(SITE_SURVEY_VERSION_NOT_MATCH, () -> service.updateSiteSurvey(updateRequest()));
        verify(mapper, never()).updateDraftIfMatch(any(), any()); verifyNoInteractions(locationFactService);
    }

    private void stubInsert() {
        when(mapper.insert(any(SiteSurveyDO.class))).thenAnswer(call -> {
            SiteSurveyDO row = call.getArgument(0); row.setId(101L); return 1;
        });
    }
    private void lock(SiteSurveyDO row) {
        when(mapper.selectByRow(new SiteSurveyRowQuery(1L, 101L))).thenReturn(row);
        when(mapper.selectForUpdate(new SiteSurveyRowQuery(1L, 101L))).thenReturn(row);
    }
    private ProjectScopeResult scope(Long version) { return new ProjectScopeResult(10L, version, Set.of(10L), Set.of()); }
    private SiteSurveyDO existing(int status) {
        var row = new SiteSurveyDO(); row.setId(101L); row.setProjectId(10L); row.setCode("SUR-1");
        row.setStatus(status); row.setVersion(4); row.setTenantId(1L); row.setLocation("核心机房");
        row.setAddressId(11L); row.setAddressVersion(1); row.setSiteId(21L); row.setSiteVersion(2);
        row.setSiteLocationId(31L); row.setSiteLocationVersion(3); row.setLocationResolutionStatus("RESOLVED");
        row.setAddressSnapshot("address-snapshot"); row.setLocationSnapshot("location-snapshot"); return row;
    }
    private SiteSurveySaveReqVO request() {
        var request = new SiteSurveySaveReqVO(); request.setProjectId(10L); request.setCode("SUR-1");
        request.setName("工勘"); request.setLocation("核心机房"); return request;
    }
    private SiteSurveySaveReqVO updateRequest() { var request = request(); request.setId(101L); request.setVersion(4); return request; }
    private LocationMaintenanceCommand command() {
        return new LocationMaintenanceCommand(999L, null, null, null, "untrusted", "fake", "999", "999");
    }
    private EngineeringLocationFactService.LocationFact resolved() {
        return new EngineeringLocationFactService.LocationFact(11L, 1, 21L, 2, 31L, 3, "RESOLVED", "address", "location");
    }
    private void error(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected.getCode(), assertThrows(ServiceException.class, action).getCode());
    }
    private void assertUnresolved(SiteSurveyDO row) {
        assertEquals("UNRESOLVED", row.getLocationResolutionStatus()); assertNull(row.getAddressId());
        assertNull(row.getAddressVersion()); assertNull(row.getSiteId()); assertNull(row.getSiteVersion());
        assertNull(row.getSiteLocationId()); assertNull(row.getSiteLocationVersion());
        assertNull(row.getAddressSnapshot()); assertNull(row.getLocationSnapshot());
    }
    private void fillOriginalFields(SiteSurveySaveReqVO request) {
        request.setSurveyDate(LocalDate.of(2026, 9, 8)); request.setSurveyorUserId(7L);
        request.setPowerSupply("供电"); request.setCabinet("机柜"); request.setNetworkPort("网口");
        request.setFiber("光纤"); request.setModule("模块"); request.setCable("线缆"); request.setGround("接地");
        request.setConstructionResource("施工资源"); request.setConclusion("结论"); request.setRemark("备注");
    }
    private boolean assertOriginalFields(SiteSurveyDO row) {
        assertEquals(LocalDate.of(2026, 9, 8), row.getSurveyDate()); assertEquals(7L, row.getSurveyorUserId());
        assertEquals("供电", row.getPowerSupply()); assertEquals("机柜", row.getCabinet()); assertEquals("网口", row.getNetworkPort());
        assertEquals("光纤", row.getFiber()); assertEquals("模块", row.getModule()); assertEquals("线缆", row.getCable());
        assertEquals("接地", row.getGround()); assertEquals("施工资源", row.getConstructionResource());
        assertEquals("结论", row.getConclusion()); assertEquals("备注", row.getRemark()); return true;
    }
}
