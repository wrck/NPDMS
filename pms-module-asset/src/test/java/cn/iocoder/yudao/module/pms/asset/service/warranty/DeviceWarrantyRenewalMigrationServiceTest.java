package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DeviceWarrantyRenewalMigrationApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRenewalRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceWarrantyRenewalMigrationServiceTest {
    DeviceWarrantyRenewalRecordMapper mapper = mock(DeviceWarrantyRenewalRecordMapper.class);
    AdminUserApi users = mock(AdminUserApi.class);
    PermissionApi permissions = mock(PermissionApi.class);
    DeviceWarrantyRenewalMigrationService service = new DeviceWarrantyRenewalMigrationService(mapper, users, permissions);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(eq(7L), any())).thenReturn(true);
        when(mapper.insertRecord(any())).thenReturn(1);
        when(mapper.updateRecord(any())).thenReturn(1);
    }
    @AfterEach void close() { TenantContextHolder.clear(); }
    static Source source(String sn, String years) {
        return new Source("same-legacy-id", "RENEW-01", sn, "53030001", "2020-01-01 00:00:00",
                "2022-04-17 00:00:00", years, "原备注\n第二行", "1", null);
    }
    static Entry entry(Source source) { return new Entry(source.sourceKey(), source, null, null); }
    static Request request(Entry... entries) { return new Request(1L, 7L, "integration:1:9", List.of(entries)); }
    static DeviceWarrantyRenewalRecordDO existing(Source source) {
        var row = DeviceWarrantyRenewalMigrationService.entity(source);
        row.setId(91L);row.setTenantId(1L);row.setVersion(4);row.setDeleted(false);
        row.setCreator("9");row.setUpdater("9");row.setCreateTime(LocalDateTime.of(2026, 1, 1, 1, 2, 3));
        row.setUpdateTime(row.getCreateTime());return row;
    }
    @Test void convertsReferenceYearsWithoutChangingActualDatesAndPreservesNulls() {
        var result = service.apply(request(entry(source("SN-1", "1")))).getFirst();
        assertEquals(12, result.fields().get("warrantyMonths"));
        assertEquals("2022-04-17", result.fields().get("warrantyEndDate"));
        assertEquals(tools.jackson.databind.node.NullNode.getInstance(), result.fields().get("isLatest"));
        assertEquals("7", result.fields().get("creator"));assertEquals(17, result.fields().size());
        assertEquals("原备注\n第二行", result.fields().get("remark"));
    }
    @Test void retainsDuplicateOriginalIdsForDifferentSerialNumbersAndEmptySerial() {
        var results = service.apply(request(entry(source("SN-1", "0")), entry(source("SN-2", "2")), entry(source("", ""))));
        assertEquals(3, results.stream().map(Result::targetId).distinct().count());
        assertEquals(0, results.getFirst().fields().get("warrantyMonths"));
        assertEquals("", results.getLast().fields().get("deviceSn"));
        assertEquals(tools.jackson.databind.node.NullNode.getInstance(), results.getLast().fields().get("warrantyMonths"));
    }
    @Test void rejectsPrecisionLossUnknownFlagAndInvalidReferenceDurationBeforeWriting() {
        Source original = source("SN", "1");
        var invalid = List.of(new Source(original.id(), "C", "SN", "G", "2020-01-01T00:00:00.001", original.endDate(), "1", null, "1", null),
                new Source(original.id(), "C", "SN", "G", original.startDate(), original.endDate(), "1", null, "1", "0"), source("SN", "-1"));
        for (var value : invalid) assertThrows(IllegalArgumentException.class, () -> service.apply(request(entry(value))));
        verify(mapper, never()).insertRecord(any());
    }
    @Test void unchangedReplayRetainsIdVersionAndEveryAuditField() {
        var source = source("SN", "1");var row = existing(source);
        when(mapper.selectForUpdate(any())).thenReturn(row);
        var result = service.apply(request(new Entry(source.sourceKey(), source, row.getId(),
                DeviceWarrantyRenewalMigrationService.fingerprint(row)))).getFirst();
        assertEquals("UNCHANGED", result.action());assertEquals(4, result.fields().get("version"));
        assertEquals("9", result.fields().get("updater"));assertEquals(row.getCreateTime().toString(), result.fields().get("updateTime"));
        verify(mapper, never()).updateRecord(any());
    }
    @Test void sourceUpdateRequiresUnchangedBaselineAndPreservesCreationAudit() {
        var row = existing(source("SN", "1"));when(mapper.selectForUpdate(any())).thenReturn(row);
        var next = source("SN", "2");String baseline = DeviceWarrantyRenewalMigrationService.fingerprint(row);
        var request = request(new Entry(next.sourceKey(), next, row.getId(), baseline));
        var result = service.apply(request).getFirst();
        assertEquals("UPDATED", result.action());assertEquals(24, result.fields().get("warrantyMonths"));
        assertEquals(5, result.fields().get("version"));assertEquals("9", result.fields().get("creator"));
        row.setRemark("later human modification");
        assertThrows(IllegalArgumentException.class, () -> service.apply(request));
    }
    @Test void rejectsCrossTenantAndUnauthorizedActor() {
        assertThrows(IllegalArgumentException.class, () -> service.apply(new Request(2L, 7L, "integration:2:9", List.of())));
        when(permissions.hasAnyPermissions(eq(7L), any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.apply(request(entry(source("SN", "1")))));
        verify(mapper, never()).insertRecord(any());
    }
    @Test void rejectsDuplicateSourceAndUnboundTargetBeforeAnyWrites() {
        var source = source("SN", "1");
        assertThrows(IllegalArgumentException.class, () -> service.apply(request(entry(source), entry(source))));
        assertThrows(IllegalArgumentException.class, () -> service.apply(request(new Entry(source.sourceKey(), source, 12L, null))));
        verify(mapper, never()).insertRecord(any());
    }
}
