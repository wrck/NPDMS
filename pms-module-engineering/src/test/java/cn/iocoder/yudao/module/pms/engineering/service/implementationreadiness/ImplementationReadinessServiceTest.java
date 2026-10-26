package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.ArrivalAcceptanceFactApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.implementationreadiness.ImplementationReadinessSnapshotDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness.*;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImplementationReadinessServiceTest {
    final ImplementationReadinessSnapshotMapper snapshots = mock(ImplementationReadinessSnapshotMapper.class);
    final ImplementationReadinessSourceMapper sources = mock(ImplementationReadinessSourceMapper.class);
    final ProjectBusinessContextApi projects = mock(ProjectBusinessContextApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    final ArrivalAcceptanceFactApi arrivals = mock(ArrivalAcceptanceFactApi.class);
    final DeliveryScopePort delivery = mock(DeliveryScopePort.class);
    final SolutionTieredReviewService reviews = mock(SolutionTieredReviewService.class);
    final ImplementationReadinessSnapshotStore store = mock(ImplementationReadinessSnapshotStore.class);
    final ImplementationReadinessService service = new ImplementationReadinessService(snapshots, sources, projects, scopes,
            devices, arrivals, delivery, reviews, store);
    final ImplementationReadinessQuery query = new ImplementationReadinessQuery(7L, 9L,
            List.of(new ImplementationReadinessQuery.ExpectedDevice(11L, "SN-11", 3L)));
    final ImplementationReadinessSnapshotDO row = new ImplementationReadinessSnapshotDO();

    @Test void roleApprovalWithoutProjectScopeRevalidatesWithoutReturningOwnerData() {
        var access = approvalAccess();
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 41L, Set.of(), Set.of()));
        when(snapshots.selectById(71L)).thenReturn(row);
        row.setTenantId(7L); row.setProjectId(9L);
        row.setDeviceScopeKey(cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(query.expectedDevices())));
        when(projects.lockCutoverReadinessRequirements(any())).thenReturn(
                new ProjectBusinessContextApi.ImplementationRequirements(21L, 3L, true, Set.of()));
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(11L, "SN-11", "room", 3L)));
        var result = service.lockAndRevalidateForApproval(approvalQuery());
        assertEquals("READY", result.decision()); assertEquals(List.of(11L), result.deviceIds());
        verify(access).requireCurrent(approvalQuery());
        verify(projects, never()).inspect(any()); verify(devices, never()).getDevice(any());
        verifyNoInteractions(scopes);
        assertEquals(Set.of("decision", "projectId", "snapshotId", "snapshotVersion", "deviceIds"),
                Arrays.stream(result.getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void invalidCurrentApprovalCannotReadAnyOwnerOrSnapshot() {
        var access = approvalAccess();
        doThrow(new SecurityException("not current")).when(access).requireCurrent(any());
        assertThrows(SecurityException.class, () -> service.lockAndRevalidateForApproval(approvalQuery()));
        verify(snapshots, never()).selectById(any()); verifyNoInteractions(projects, devices, sources);
    }

    @Test void crossTenantApprovalCannotReachAuthorizationProvider() {
        var access = approvalAccess(); TenantContextHolder.setTenantId(8L);
        assertThrows(ImplementationReadinessException.class, () -> service.lockAndRevalidateForApproval(approvalQuery()));
        verifyNoInteractions(access, projects, devices, sources);
    }

    @Test void exactSnapshotVersionIsMandatoryForApproval() {
        approvalAccess(); when(snapshots.selectById(71L)).thenReturn(row);
        row.setTenantId(7L); row.setProjectId(9L);
        var q = approvalQuery();
        var changed = new cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query(
                q.tenantId(), q.taskId(), q.taskVersion(), q.approvalInstanceId(), q.approvalVersion(), q.nodeNo(), q.projectId(), q.snapshotId(), 1L, q.deviceIds());
        assertThrows(ImplementationReadinessException.class, () -> service.lockAndRevalidateForApproval(changed));
        verifyNoInteractions(projects, devices, sources);
    }

    cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi approvalAccess() {
        var access = mock(cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.class);
        org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(access);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "approvalAccess", provider);
        return access;
    }

    cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query approvalQuery() {
        return new cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query(
                7L, 81L, 2L, 91L, 3L, 4, 9L, 71L, 0L, List.of(11L));
    }

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(13L).setTenantId(7L), new MockHttpServletRequest());
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 41L, Set.of(9L), Set.of()));
        when(projects.inspect(any())).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(7L).projectId(9L).projectVersion(5L).scopeVersion(41L).build());
        when(projects.implementationRequirements(any())).thenReturn(
                new ProjectBusinessContextApi.ImplementationRequirements(21L, 3L, true, Set.of()));
        when(devices.lockProjectInstallationDevices(any())).thenReturn(List.of(new ProjectDeviceInstallationFact(11L, "SN-11", "room")));
        when(devices.getDevice(11L)).thenReturn(new DeviceSummaryDTO(11L, 7L, "SN-11", null, null, null,
                null, null, null, null, 9L, 3L, null, null, null, null, null, null, null, null, null));
        var solution = new SolutionDO(); solution.setId(31L); solution.setVersion(4L); solution.setReviewLevel(0);
        when(sources.approvedSolutions(any())).thenReturn(List.of(solution));
        when(snapshots.latest(any())).thenReturn(row);
        stored(context(21L, 3L, Set.of()));
    }

    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void explicitAllNotApplicableUsesNoArrivalInstallationConfigurationOrJointTestOwnerFacts() {
        var result = service.inspect(query);
        assertEquals(ImplementationReadinessResult.Decision.READY, result.decision());
        verify(sources, never()).installations(any()); verify(sources, never()).configurations(any()); verify(sources, never()).jointTests(any());
        verifyNoInteractions(arrivals, delivery); verify(sources).approvedSolutions(any()); verify(devices).getDevice(11L);
        assertTrue(result.currentContext().sourceFacts().stream().allMatch(f -> f.completionStatus() == CompletionStatus.NOT_APPLICABLE));
    }

    @Test void explicitAllNotApplicableStillRequiresApprovedImplementationSolution() {
        when(sources.approvedSolutions(any())).thenReturn(List.of());
        var failure = assertThrows(ImplementationReadinessException.class, () -> service.inspect(query));
        assertEquals(ImplementationReadinessException.Code.SNAPSHOT_NOT_FOUND, failure.getCode());
        verifyNoInteractions(arrivals, delivery);
    }

    @Test void requiredHardwareWithNoInstallationRecordIsNotReady() {
        when(projects.implementationRequirements(any())).thenReturn(
                new ProjectBusinessContextApi.ImplementationRequirements(21L, 3L, true, Set.of("EXE_02")));
        when(sources.installations(any())).thenReturn(List.of());
        stored(context(21L, 3L, Set.of(SourceCode.EXE_02)));
        var result = service.inspect(query);
        assertEquals(ImplementationReadinessResult.Decision.NOT_READY, result.decision());
        assertEquals(List.of("EXE_02_NOT_COMPLETED"), result.currentUnmetCodes());
        verify(sources).installations(any()); verify(sources, never()).configurations(any()); verifyNoInteractions(arrivals);
    }

    @Test void changedPolicyOrPlanVersionMakesPriorSnapshotStale() {
        when(projects.implementationRequirements(any())).thenReturn(
                new ProjectBusinessContextApi.ImplementationRequirements(21L, 4L, true, Set.of()));
        assertEquals(ImplementationReadinessResult.Decision.STALE, service.inspect(query).decision());
        when(projects.implementationRequirements(any())).thenReturn(
                new ProjectBusinessContextApi.ImplementationRequirements(22L, 3L, true, Set.of("EXE_02")));
        when(sources.installations(any())).thenReturn(List.of());
        var changedPolicy = service.inspect(query);
        assertEquals(ImplementationReadinessResult.Decision.STALE, changedPolicy.decision());
        assertEquals(List.of("IMPLEMENTATION_SOURCE_CHANGED"), changedPolicy.staleReasonCodes());
        verify(snapshots, never()).insert(any(ImplementationReadinessSnapshotDO.class));
    }

    void stored(ImplementationReadinessContextFact fact) {
        var unmet = fact.isReady() ? List.<String>of() : List.of("EXE_02_NOT_COMPLETED");
        when(store.read(row)).thenReturn(new ImplementationReadinessSnapshotFact(7L, 9L, 71L, 1, 0L, "CUTOVER",
                fact.isReady() ? ImplementationReadinessSnapshotFact.Decision.READY : ImplementationReadinessSnapshotFact.Decision.NOT_READY,
                fact, unmet, LocalDateTime.of(2026, 10, 15, 10, 0)));
    }

    ImplementationReadinessContextFact context(Long planId, Long planVersion, Set<SourceCode> required) {
        var facts = Arrays.stream(SourceCode.values()).map(code -> new SourceFact(code,
                required.contains(code) ? CompletionStatus.NOT_COMPLETED : CompletionStatus.NOT_APPLICABLE,
                0L, List.of(), List.of(), false)).toList();
        return new ImplementationReadinessContextFact(41L, List.of(new DeviceFact(11L, "SN-11", 3L)),
                new ApprovedPlanFact(31L, 4L), facts, new PlanApplicability(planId, planVersion, required));
    }
}
