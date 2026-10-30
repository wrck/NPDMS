package cn.iocoder.yudao.module.pms.project.api.stageplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.PaymentAcceptanceTarget;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.PaymentAcceptanceMapper;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPaymentAcceptanceApiImplTest {
    final PaymentAcceptanceMapper mapper = mock(PaymentAcceptanceMapper.class);
    final ProjectPaymentAcceptanceApiImpl api = new ProjectPaymentAcceptanceApiImpl(mapper);
    final LocalDateTime date = LocalDateTime.of(2026, 12, 31, 12, 0);
    PaymentAcceptanceTarget target;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        target = new PaymentAcceptanceTarget(); target.setId(11L); target.setProjectId(7L); target.setVersion(3);
        when(mapper.selectTargets(any())).thenReturn(List.of(target));
        when(mapper.updateAcceptance(any())).thenReturn(1);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    ProjectPaymentAcceptanceApi.Entry entry(String key) {
        return new ProjectPaymentAcceptanceApi.Entry(key, "CONTRACT-1", "Final acceptance", "STAGE", "S5", date, null);
    }
    ProjectPaymentAcceptanceApi.Request request(ProjectPaymentAcceptanceApi.Entry... entries) {
        return new ProjectPaymentAcceptanceApi.Request(1L, "integration:1:9", List.of(entries), false);
    }
    @Test void previewDoesNotBindOrWrite() {
        assertEquals("UPDATED", api.preview(request(entry("1"))).getFirst().action());
        verify(mapper, never()).bind(any()); verify(mapper, never()).updateAcceptance(any());
    }
    @Test void refreshBindsSourceAndOnlyUpdatesAcceptance() {
        var result = api.refresh(request(entry("1"))).getFirst();
        assertNotNull(result.targetId()); assertEquals("UPDATED", result.action());
        verify(mapper).updateAcceptance(argThat(q -> q.nodeId().equals(11L) && q.expectedVersion() == 3 && date.equals(q.acceptanceTime())));
    }
    @Test void replayIsIdempotent() {
        target.setBindingId(90L); target.setSourceOwner("integration:1:9"); target.setSourceKey("1"); target.setAcceptanceTime(date);
        assertEquals("UNCHANGED", api.refresh(request(entry("1"))).getFirst().action());
        verify(mapper, never()).bind(any()); verify(mapper, never()).updateAcceptance(any());
    }
    @Test void ambiguousContractAndDuplicateTargetsProduceIssuesWithoutWrites() {
        when(mapper.selectTargets(any())).thenReturn(List.of(target, target));
        assertEquals("ISSUE", api.refresh(request(entry("1"))).getFirst().action());
        when(mapper.selectTargets(any())).thenReturn(List.of(target));
        assertTrue(api.refresh(request(entry("1"), entry("2"))).stream().allMatch(r -> r.action().equals("ISSUE")));
        verify(mapper, never()).updateAcceptance(any());
    }
    @Test void missingMappingAndOtherSourceCannotOverwrite() {
        var unmapped = new ProjectPaymentAcceptanceApi.Entry("1", "CONTRACT-1", "Final", null, null, date, null);
        assertEquals("ISSUE", api.refresh(request(unmapped)).getFirst().action());
        target.setSourceOwner("integration:1:8"); target.setSourceKey("1");
        assertEquals("ISSUE", api.refresh(request(entry("1"))).getFirst().action());
        verify(mapper, never()).updateAcceptance(any());
    }
    @Test void tenantAndConcurrentChangesCannotBeBypassed() {
        assertThrows(IllegalArgumentException.class, () -> api.refresh(new ProjectPaymentAcceptanceApi.Request(2L,"integration:2:9",List.of(entry("1")),false)));
        when(mapper.updateAcceptance(any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> api.refresh(request(entry("1"))));
    }
}
