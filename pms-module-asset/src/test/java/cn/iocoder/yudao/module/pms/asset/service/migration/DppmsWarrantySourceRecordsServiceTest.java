package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query.*;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DppmsWarrantySourceRecordsServiceTest {
    private final DeviceWarrantyServiceRecordMapper services = mock(DeviceWarrantyServiceRecordMapper.class);
    private final DeviceWarrantyRegistrationMapper registrations = mock(DeviceWarrantyRegistrationMapper.class);
    private final DeviceWarrantyChangeRecordMapper changes = mock(DeviceWarrantyChangeRecordMapper.class);
    private final DeviceWarrantyFactProjectMapper relations = mock(DeviceWarrantyFactProjectMapper.class);
    private final ProjectDeviceAssignmentGuardApi guard = mock(ProjectDeviceAssignmentGuardApi.class);
    private final DeviceAccessScopeService scope = mock(DeviceAccessScopeService.class);
    private final DppmsWarrantySourceRecordsService service =
            new DppmsWarrantySourceRecordsService(services, registrations, changes, relations, guard, scope);
    private final Map<WarrantyFactSourceIdentityQuery, DeviceWarrantyServiceRecordDO> serviceRows = new HashMap<>();
    private final Map<WarrantyFactSourceIdentityQuery, DeviceWarrantyRegistrationDO> registrationRows = new HashMap<>();
    private final Map<WarrantyFactSourceIdentityQuery, DeviceWarrantyChangeRecordDO> changeRows = new HashMap<>();
    private final Map<WarrantyFactProjectIdentityQuery, DeviceWarrantyFactProjectDO> relationRows = new HashMap<>();
    private final LocalDateTime date = LocalDateTime.of(2023, 1, 2, 3, 4, 5);
    private final LocalDateTime readAt = LocalDateTime.of(2026, 10, 8, 9, 10, 11);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(guard.validate(any())).thenAnswer(invocation -> {
            var query = (cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery) invocation.getArgument(0);
            return new ProjectDeviceAssignmentGuardResult(query.projectId(), query.tenantId(), 50L, query.projectId(), 1L, true, null);
        });
        when(scope.visibleProjectIds(1L, 7L)).thenReturn(Set.of(100L, 200L));
        when(services.selectSourceForUpdate(any())).thenAnswer(i -> serviceRows.get(i.getArgument(0)));
        when(registrations.selectSourceForUpdate(any())).thenAnswer(i -> registrationRows.get(i.getArgument(0)));
        when(changes.selectSourceForUpdate(any())).thenAnswer(i -> changeRows.get(i.getArgument(0)));
        when(relations.selectProjectRelationForUpdate(any())).thenAnswer(i -> relationRows.get(i.getArgument(0)));
        when(services.insert(any(DeviceWarrantyServiceRecordDO.class))).thenAnswer(i -> {
            DeviceWarrantyServiceRecordDO row = i.getArgument(0);
            serviceRows.put(new WarrantyFactSourceIdentityQuery(row.getTenantId(), row.getSourceTable(),
                    row.getSourceKey(), row.getSourceChecksum()), row); return 1;
        });
        when(registrations.insert(any(DeviceWarrantyRegistrationDO.class))).thenAnswer(i -> {
            DeviceWarrantyRegistrationDO row = i.getArgument(0);
            registrationRows.put(new WarrantyFactSourceIdentityQuery(row.getTenantId(), row.getSourceTable(),
                    row.getSourceKey(), row.getSourceChecksum()), row); return 1;
        });
        when(changes.insert(any(DeviceWarrantyChangeRecordDO.class))).thenAnswer(i -> {
            DeviceWarrantyChangeRecordDO row = i.getArgument(0);
            changeRows.put(new WarrantyFactSourceIdentityQuery(row.getTenantId(), row.getSourceTable(),
                    row.getSourceKey(), row.getSourceChecksum()), row); return 1;
        });
        when(relations.insert(any(DeviceWarrantyFactProjectDO.class))).thenAnswer(i -> {
            DeviceWarrantyFactProjectDO row = i.getArgument(0);
            relationRows.put(new WarrantyFactProjectIdentityQuery(row.getTenantId(), row.getProjectId(),
                    row.getFactType(), row.getFactId()), row); return 1;
        });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    private ServiceRecord serviceFact(String key, String remark) {
        return new ServiceRecord(key, "nonunique-source-id", "CON-XB", " RAW-SN ", "RAW-GRADE",
                date, date.plusYears(2), "X", remark, 7, -3);
    }
    private Registration registration() {
        return new Registration(301L, 41, 42, "RAW-SN", "OFFICE", "SOURCE-PROJECT",
                "CONTRACT", 17, "CUSTOMER", "ITEM", "ITEM-NAME", "LEVEL",
                date, date.plusYears(3), 3);
    }
    private Change change() {
        return new Change(401L, "operator", 19, "RAW-SN", date, date.plusYears(4), 5, date.plusDays(1));
    }
    private Command fullCommand() {
        return new Command(1L, 7L, 100L, "101", "v1", readAt,
                List.of(serviceFact("content-occurrence-1", "REMARK")), List.of(registration()), List.of(change()));
    }
    private Command serviceCommand(Long project, String sourceProject, ServiceRecord row) {
        return new Command(1L, 7L, project, sourceProject, "v1", readAt, List.of(row), List.of(), List.of());
    }
    private void noInserts() {
        verify(services, never()).insert(any(DeviceWarrantyServiceRecordDO.class));
        verify(registrations, never()).insert(any(DeviceWarrantyRegistrationDO.class));
        verify(changes, never()).insert(any(DeviceWarrantyChangeRecordDO.class));
        verify(relations, never()).insert(any(DeviceWarrantyFactProjectDO.class));
    }

    @Test void allThirtyThreeOriginalFieldsBecomeTypedFactsWithRawCodesAndProjectRelations() {
        var result = service.importRecords(fullCommand());
        assertEquals(3, result.size());
        assertEquals(Set.of("warranty-services", "warranty-registrations", "warranty-changes"),
                new HashSet<>(result.stream().map(r -> r.domain()).toList()));
        assertTrue(result.stream().allMatch(r -> r.targets().size() == 2 && r.reasonCode() == null));
        assertEquals(3, relationRows.size());
        var a = serviceRows.values().iterator().next();
        assertEquals("nonunique-source-id", a.getSourceId()); assertEquals("CON-XB", a.getRenewalContractNo());
        assertEquals(" RAW-SN ", a.getDeviceSn()); assertEquals("RAW-GRADE", a.getGrade());
        assertEquals(date, a.getBeginDate()); assertEquals(date.plusYears(2), a.getEndDate());
        assertEquals("X", a.getWarrantyFlag()); assertEquals("REMARK", a.getRemark());
        assertEquals(7, a.getExtensionFlag()); assertEquals(-3, a.getSourceState());
        assertEquals("fb_service", a.getSourceTable()); assertEquals("DPPMS", a.getSourceSystem());
        assertEquals(1L, a.getTenantId()); assertEquals(readAt, a.getSourceReadAt()); assertEquals("7", a.getCreator());
        var b = registrationRows.values().iterator().next();
        assertEquals(301L, b.getSourceId()); assertEquals(41, b.getPackageSyncId()); assertEquals(42, b.getContractSyncId());
        assertEquals("RAW-SN", b.getDeviceSn()); assertEquals("OFFICE", b.getOfficeCode());
        assertEquals("SOURCE-PROJECT", b.getSourceProjectName()); assertEquals("CONTRACT", b.getContractNo());
        assertEquals(17, b.getContractType()); assertEquals("CUSTOMER", b.getCustomerName());
        assertEquals("ITEM", b.getItemCode()); assertEquals("ITEM-NAME", b.getItemName());
        assertEquals("LEVEL", b.getWarrantyLevel()); assertEquals(date, b.getWarrantyStartTime());
        assertEquals(date.plusYears(3), b.getWarrantyEndTime()); assertEquals(3, b.getWarrantyLimit());
        var c = changeRows.values().iterator().next();
        assertEquals(401L, c.getSourceId()); assertEquals("operator", c.getSourceUsername());
        assertEquals(19, c.getUpdateType()); assertEquals("RAW-SN", c.getDeviceSn());
        assertEquals(date, c.getWarrantyStartTime()); assertEquals(date.plusYears(4), c.getWarrantyEndTime());
        assertEquals(5, c.getWarrantyTimes()); assertEquals(date.plusDays(1), c.getSourceUpdatedAt());
        assertTrue(relationRows.values().stream().allMatch(r -> r.getProjectId().equals(100L)
                && r.getSourceProjectKey().equals("101") && r.getTenantId().equals(1L)));
    }

    @Test void replayAndCrossProjectHistoryShareGlobalFactWithoutOverwritingOriginalReadEvidence() {
        var first = service.importRecords(fullCommand());
        var original = serviceRows.values().iterator().next();
        clearInvocations(services, registrations, changes, relations);
        assertEquals(first, service.importRecords(fullCommand()));
        noInserts();
        var another = new Command(1L, 7L, 200L, "202", "v2", readAt.plusDays(1),
                fullCommand().services(), fullCommand().registrations(), fullCommand().changes());
        var result = service.importRecords(another);
        assertEquals(1, serviceRows.size()); assertEquals(1, registrationRows.size()); assertEquals(1, changeRows.size());
        assertEquals(6, relationRows.size()); assertEquals(first.getFirst().targets().getFirst(), result.getFirst().targets().getFirst());
        assertEquals("v1", original.getSourceVersion()); assertEquals(readAt, original.getSourceReadAt());
        assertEquals(3, relationRows.values().stream().filter(r -> r.getProjectId().equals(200L)).count());
        verify(services, never()).updateById(any(DeviceWarrantyServiceRecordDO.class));
        verify(registrations, never()).updateById(any(DeviceWarrantyRegistrationDO.class));
        verify(changes, never()).updateById(any(DeviceWarrantyChangeRecordDO.class));
    }

    @Test void changedOriginalContentCreatesNewRevisionAndNeverEditsPreviousFact() {
        service.importRecords(serviceCommand(100L, "101", serviceFact("same-captured-key", "before")));
        var original = serviceRows.values().iterator().next();
        service.importRecords(serviceCommand(100L, "101", serviceFact("same-captured-key", "after")));
        assertEquals(2, serviceRows.size()); assertEquals(2, relationRows.size());
        assertEquals("before", original.getRemark());
        assertEquals(2, serviceRows.values().stream().map(r -> r.getSourceChecksum()).distinct().count());
        verify(services, never()).updateById(any(DeviceWarrantyServiceRecordDO.class));
    }

    @Test void nullableNonuniqueServiceIdAndRepeatedMultisetRowsDoNotCollapseOrNormalizeRawValues() {
        var empty = new ServiceRecord("occurrence-1", null, "", " SN  ", null, null, null, "", "", null, null);
        var repeated = new ServiceRecord("occurrence-2", null, "", " SN  ", null, null, null, "", "", null, null);
        var nullRemark = new ServiceRecord("occurrence-3", null, "", " SN  ", null, null, null, "", null, null, null);
        var result = service.importRecords(new Command(1L, 7L, 100L, "101", "v1", readAt,
                List.of(empty, repeated, nullRemark), List.of(), List.of()));
        assertEquals(3, result.size()); assertEquals(3, serviceRows.size());
        assertEquals(2, serviceRows.values().stream().map(r -> r.getSourceChecksum()).distinct().count());
        assertTrue(serviceRows.values().stream().allMatch(r -> r.getSourceId() == null && r.getBeginDate() == null
                && r.getEndDate() == null && r.getSourceState() == null && r.getDeviceSn().equals(" SN  ")));
        assertEquals(2, serviceRows.values().stream().filter(r -> "".equals(r.getRemark())).count());
        assertEquals(1, serviceRows.values().stream().filter(r -> r.getRemark() == null).count());
    }

    @Test void nullableRegistrationAndChangeFieldsRemainNullAndEmptyTextRemainsEmpty() {
        var emptyRegistration = new Registration(302L, null, null, "", null, "", null,
                null, "", null, "", null, null, null, null);
        var emptyChange = new Change(402L, "", null, null, null, null, null, null);
        service.importRecords(new Command(1L, 7L, 100L, "101", "v1", readAt,
                List.of(), List.of(emptyRegistration), List.of(emptyChange)));
        var registration = registrationRows.values().iterator().next();
        assertNull(registration.getPackageSyncId()); assertNull(registration.getContractSyncId());
        assertEquals("", registration.getDeviceSn()); assertNull(registration.getOfficeCode());
        assertEquals("", registration.getSourceProjectName()); assertNull(registration.getContractNo());
        assertNull(registration.getContractType()); assertEquals("", registration.getCustomerName());
        assertNull(registration.getItemCode()); assertEquals("", registration.getItemName());
        assertNull(registration.getWarrantyLevel()); assertNull(registration.getWarrantyStartTime());
        assertNull(registration.getWarrantyEndTime()); assertNull(registration.getWarrantyLimit());
        var change = changeRows.values().iterator().next();
        assertEquals("", change.getSourceUsername()); assertNull(change.getUpdateType());
        assertNull(change.getDeviceSn()); assertNull(change.getWarrantyStartTime());
        assertNull(change.getWarrantyEndTime()); assertNull(change.getWarrantyTimes());
        assertNull(change.getSourceUpdatedAt());
    }

    @Test void deletedOrCorruptFactAndDeletedRelationRejectReplayWithoutRepairingHistory() {
        var command = serviceCommand(100L, "101", serviceFact("key", "original"));
        service.importRecords(command);
        var existing = serviceRows.values().iterator().next();
        existing.setDeleted(true);
        clearInvocations(services, registrations, changes, relations);
        assertThrows(IllegalStateException.class, () -> service.importRecords(command)); noInserts();
        existing.setDeleted(false); existing.setRemark("corrupted");
        assertThrows(IllegalStateException.class, () -> service.importRecords(command)); noInserts();
        existing.setRemark("original");
        relationRows.values().iterator().next().setDeleted(true);
        assertThrows(IllegalStateException.class, () -> service.importRecords(command)); noInserts();
        relationRows.values().iterator().next().setDeleted(false);
        assertThrows(IllegalStateException.class, () -> service.importRecords(
                serviceCommand(100L, "wrong-source-project", serviceFact("key", "original")))); noInserts();
        verify(services, never()).updateById(any(DeviceWarrantyServiceRecordDO.class));
        verify(relations, never()).updateById(any(DeviceWarrantyFactProjectDO.class));
    }

    @Test void tenantAndUnauthorizedProjectRejectBeforeAnyBusinessMapper() {
        var full = fullCommand();
        assertThrows(IllegalArgumentException.class, () -> service.importRecords(new Command(2L, 7L, 100L,
                "101", "v1", readAt, full.services(), full.registrations(), full.changes())));
        verifyNoInteractions(services, registrations, changes, relations, guard);
        doReturn(new ProjectDeviceAssignmentGuardResult(100L, 1L, 50L, 100L, 1L, false, "FORBIDDEN")).when(guard).validate(any());
        assertThrows(IllegalArgumentException.class, () -> service.importRecords(full));
        verifyNoInteractions(services, registrations, changes, relations);
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> service.importRecords(full));
        verifyNoInteractions(services, registrations, changes, relations);
    }

    @Test void missingCapturedKeyAndInvalidLaterRecordFailBeforeFirstFactIsInserted() {
        assertThrows(IllegalArgumentException.class, () -> service.importRecords(serviceCommand(100L, "101",
                new ServiceRecord(null, "id", null, null, null, null, null, null, null, null, null))));
        var invalidLater = new Command(1L, 7L, 100L, "101", "v1", readAt,
                fullCommand().services(), List.of(), List.of(new Change(402L, null, null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.importRecords(invalidLater));
        verifyNoInteractions(services, registrations, changes, relations, guard);
    }

    @Test void invisibleProjectAndCrossTenantReadDoNotReachBusinessMappers() {
        when(scope.visibleProjectIds(1L, 7L)).thenReturn(Set.of());
        assertEquals(new Page(List.of(), 0L), service.page(new Query(1L, 7L, 100L, "warranty-services", 1, 20)));
        assertThrows(IllegalArgumentException.class, () -> service.page(new Query(2L, 7L, 100L, "warranty-services", 1, 20)));
        assertThrows(IllegalArgumentException.class, () -> service.page(new Query(1L, 7L, 100L, "unknown", 1, 20)));
        verifyNoInteractions(services, registrations, changes, relations);
    }

    @Test void authorizedPageReturnsEveryOriginalColumnIncludingNullAndUsesOnlyProjectRelationQuery() {
        service.importRecords(fullCommand());
        var a = serviceRows.values().iterator().next();
        var b = registrationRows.values().iterator().next();
        var c = changeRows.values().iterator().next();
        a.setRemark(null);
        var pageQuery = new WarrantyFactProjectPageQuery(1L, 100L, 20L, 20);
        when(services.selectProjectPage(pageQuery)).thenReturn(List.of(a)); when(services.countProject(pageQuery)).thenReturn(1L);
        when(registrations.selectProjectPage(pageQuery)).thenReturn(List.of(b)); when(registrations.countProject(pageQuery)).thenReturn(1L);
        when(changes.selectProjectPage(pageQuery)).thenReturn(List.of(c)); when(changes.countProject(pageQuery)).thenReturn(1L);
        var servicePage = service.page(new Query(1L, 7L, 100L, "warranty-services", 2, 20));
        var registrationPage = service.page(new Query(1L, 7L, 100L, "warranty-registrations", 2, 20));
        var changePage = service.page(new Query(1L, 7L, 100L, "warranty-changes", 2, 20));
        assertEquals(13, servicePage.list().getFirst().values().size());
        assertTrue(servicePage.list().getFirst().values().containsKey("remark"));
        assertNull(servicePage.list().getFirst().values().get("remark"));
        assertEquals(" RAW-SN ", servicePage.list().getFirst().values().get("barcode"));
        assertEquals(18, registrationPage.list().getFirst().values().size());
        assertEquals(11, changePage.list().getFirst().values().size());
        verify(services).selectProjectPage(pageQuery); verify(registrations).selectProjectPage(pageQuery);
        verify(changes).selectProjectPage(pageQuery);
        assertFalse(servicePage.list().getFirst().values().containsKey("deviceId"));
        assertFalse(registrationPage.list().getFirst().values().containsKey("projectId"));
    }
}
