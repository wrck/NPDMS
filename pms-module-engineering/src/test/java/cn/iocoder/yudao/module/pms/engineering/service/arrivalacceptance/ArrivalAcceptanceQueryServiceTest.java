package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalDifferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalPageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArrivalAcceptanceQueryServiceTest {

    @Test
    void emptyVisibleScopeReturnsEmptyPageWithoutQuery() {
        Fixture fixture = fixture();

        PageResult<ArrivalAcceptanceViews.ArrivalListItem> page = fixture.service().page(
                new ArrivalAcceptanceViews.PageRequest(1L, null, null, null, 1, 20,
                        access(Set.of(), Set.of(), Set.of(), Set.of(), Set.of())));

        assertEquals(List.of(), page.getList());
        assertEquals(0L, page.getTotal());
        verify(fixture.acceptanceMapper(), never()).selectPageRows(any());
        verify(fixture.acceptanceMapper(), never()).selectPageCount(any());
    }

    @Test
    void pageUsesExactFiltersAndProjectsAllowedActionsFromTrustedFacts() {
        Fixture fixture = fixture();
        ArrivalAcceptanceDO row = draft();
        when(fixture.acceptanceMapper().selectPageRows(any())).thenReturn(List.of(row));
        when(fixture.acceptanceMapper().selectPageCount(any())).thenReturn(1L);
        when(fixture.lineMapper().selectCurrentListByAcceptanceIds(any())).thenReturn(List.of(deviceLine()));
        when(fixture.differenceMapper().selectCurrentListByAcceptanceIds(any())).thenReturn(List.of());
        when(fixture.evidenceMapper().selectByArrivalAcceptanceIds(any())).thenReturn(List.of());

        PageResult<ArrivalAcceptanceViews.ArrivalListItem> page = fixture.service().page(
                new ArrivalAcceptanceViews.PageRequest(1L, 100L, " B-1 ", "DRAFT", 2, 10,
                        access(Set.of(ArrivalAcceptanceViews.PERMISSION_EDIT_OWN_DRAFT,
                                        ArrivalAcceptanceViews.PERMISSION_RESOLVE_DIFFERENCE),
                                Set.of(100L), Set.of(100L), Set.of(), Set.of(100L))));

        assertEquals(1L, page.getTotal());
        assertEquals(List.of("EDIT_DRAFT", "RAISE_DIFFERENCE"),
                page.getList().getFirst().allowedActions());
        ArgumentCaptor<ArrivalPageQuery> query = ArgumentCaptor.forClass(ArrivalPageQuery.class);
        verify(fixture.acceptanceMapper()).selectPageRows(query.capture());
        assertEquals(100L, query.getValue().projectId());
        assertEquals("B-1", query.getValue().batchCode());
        assertEquals(10, query.getValue().offset());
        assertEquals(10, query.getValue().limit());
        verify(fixture.lineMapper(), never()).selectCurrentList(any());
        verify(fixture.differenceMapper(), never()).selectCurrentList(any());
        verify(fixture.evidenceMapper(), never()).selectBySource(any());
    }

    @Test
    void duplicateEvidenceRootsFailClosedBeforeProjection() {
        Fixture fixture = fixture();
        when(fixture.acceptanceMapper().selectPageRows(any())).thenReturn(List.of(draft()));
        when(fixture.lineMapper().selectCurrentListByAcceptanceIds(any())).thenReturn(List.of());
        when(fixture.differenceMapper().selectCurrentListByAcceptanceIds(any())).thenReturn(List.of());
        DeliveryEvidenceDO first = evidence(1L);
        DeliveryEvidenceDO second = evidence(2L);
        when(fixture.evidenceMapper().selectByArrivalAcceptanceIds(any())).thenReturn(List.of(first, second));

        assertThrows(IllegalStateException.class, () -> fixture.service().page(
                new ArrivalAcceptanceViews.PageRequest(1L, null, null, null, 1, 20,
                        access(Set.of(), Set.of(100L), Set.of(), Set.of(), Set.of()))));
    }

    @Test
    void draftDetailWithoutEvidenceReturnsJsonNullProjection() {
        Fixture fixture = fixture();
        when(fixture.acceptanceMapper().selectRow(any())).thenReturn(draft());
        when(fixture.lineMapper().selectCurrentList(any())).thenReturn(List.of(deviceLine()));
        when(fixture.differenceMapper().selectCurrentList(any())).thenReturn(List.of());
        when(fixture.evidenceMapper().selectBySource(any())).thenReturn(null);

        ArrivalAcceptanceViews.ArrivalDetail detail = fixture.service().detail(
                new ArrivalAcceptanceViews.DetailRequest(1L, 900L,
                        access(Set.of(), Set.of(100L), Set.of(), Set.of(), Set.of())));

        assertNull(detail.evidence());
        assertEquals("Signer", detail.signerName());
        assertEquals(11L, detail.currentLines().getFirst().deviceId());
    }

    @Test
    void invisibleDetailUsesSingleNotVisibleFailure() {
        Fixture fixture = fixture();
        when(fixture.acceptanceMapper().selectRow(any())).thenReturn(draft());

        assertThrows(ArrivalAcceptanceQueryService.NotVisibleException.class, () -> fixture.service().detail(
                new ArrivalAcceptanceViews.DetailRequest(1L, 900L,
                        access(Set.of(), Set.of(200L), Set.of(), Set.of(), Set.of()))));

        verify(fixture.lineMapper(), never()).selectCurrentList(any());
    }

    private static Fixture fixture() {
        ArrivalAcceptanceMapper acceptance = mock(ArrivalAcceptanceMapper.class);
        ArrivalLineMapper line = mock(ArrivalLineMapper.class);
        ArrivalDifferenceMapper difference = mock(ArrivalDifferenceMapper.class);
        DeliveryEvidenceMapper evidence = mock(DeliveryEvidenceMapper.class);
        DeliveryEvidenceRevisionMapper revision = mock(DeliveryEvidenceRevisionMapper.class);
        return new Fixture(acceptance, line, difference, evidence, revision,
                new ArrivalAcceptanceQueryService(acceptance, line, difference, evidence, revision));
    }

    private static ArrivalAcceptanceViews.AccessContext access(Set<String> permissions,
                                                                Set<Long> visible,
                                                                Set<Long> editable,
                                                                Set<Long> managers,
                                                                Set<Long> team) {
        return new ArrivalAcceptanceViews.AccessContext(8L, permissions, visible, editable, managers, team);
    }

    private static ArrivalAcceptanceDO draft() {
        ArrivalAcceptanceDO row = new ArrivalAcceptanceDO();
        row.setId(900L);
        row.setTenantId(1L);
        row.setProjectId(100L);
        row.setBatchCode("B-1");
        row.setLogisticsNo("L-1");
        row.setArrivedAt(LocalDateTime.of(2026, 8, 30, 8, 0));
        row.setSignerSnapshot(JsonUtils.toJsonString(new ArrivalAcceptanceViews.SignerSnapshot("Signer")));
        row.setStatus("DRAFT");
        row.setDeliveryScopeVersion(8L);
        row.setScopeWatermark(JsonUtils.toJsonString(new ArrivalScopeWatermark(8L, java.util.Map.of(11L, 9L))));
        row.setVersion(0);
        row.setCreator("8");
        row.setCreateTime(LocalDateTime.of(2026, 8, 30, 8, 1));
        return row;
    }

    private static ArrivalLineDO deviceLine() {
        ArrivalLineDO line = new ArrivalLineDO();
        line.setId(10L);
        line.setLineNo(1);
        line.setLineRevision(1);
        line.setScopeType("DEVICE");
        line.setDeviceId(11L);
        line.setDeviceAssignmentVersion(9L);
        line.setExpectedQuantity(java.math.BigDecimal.ONE);
        line.setAcceptedQuantity(java.math.BigDecimal.ZERO);
        line.setUnit("台");
        line.setStatus("NOT_ARRIVED");
        line.setVersion(0);
        line.setArrivalAcceptanceId(900L);
        return line;
    }

    private static DeliveryEvidenceDO evidence(Long id) {
        DeliveryEvidenceDO evidence = new DeliveryEvidenceDO();
        evidence.setId(id);
        evidence.setSourceObjectId(900L);
        evidence.setAccSyncStatus("NOT_PUBLISHED");
        return evidence;
    }

    private record Fixture(ArrivalAcceptanceMapper acceptanceMapper,
                           ArrivalLineMapper lineMapper,
                           ArrivalDifferenceMapper differenceMapper,
                           DeliveryEvidenceMapper evidenceMapper,
                           DeliveryEvidenceRevisionMapper revisionMapper,
                           ArrivalAcceptanceQueryService service) {
    }
}
