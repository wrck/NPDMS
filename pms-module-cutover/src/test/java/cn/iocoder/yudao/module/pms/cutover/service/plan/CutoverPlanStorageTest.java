package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.*;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistResultStore;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverPlanStorageTest {
    private final CutoverPlanDeviceMapper devices = mock(CutoverPlanDeviceMapper.class);
    private final CutoverPlanScheduleMapper schedules = mock(CutoverPlanScheduleMapper.class);
    private final CutoverPlanFileMapper files = mock(CutoverPlanFileMapper.class);
    private final CutoverPlanRiskMitigationMapper risks = mock(CutoverPlanRiskMitigationMapper.class);
    private final CutoverPlanSupportContactChangeMapper changes = mock(CutoverPlanSupportContactChangeMapper.class);
    private final CutoverSupportArrangementMapper supports = mock(CutoverSupportArrangementMapper.class);
    private final CutoverAssessmentDeviceMapper assessments = mock(CutoverAssessmentDeviceMapper.class);
    private final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
    private final CutoverChecklistItemResultMapper results = mock(CutoverChecklistItemResultMapper.class);
    private final CutoverChecklistResultStore resultStorage = mock(CutoverChecklistResultStore.class);
    private final CutoverConfigurationMetadataStore metadata = mock(CutoverConfigurationMetadataStore.class);
    private final CutoverPlanStorageMapper drafts = mock(CutoverPlanStorageMapper.class);
    private final CutoverPlanStorage storage = new CutoverPlanStorage(devices, schedules, files, risks, changes,
            supports, assessments, items, results, resultStorage, metadata, drafts);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void persistsAndProjectsPlanTimeWithoutChangingTaskOrScheduleDetails() {
        var row = new CutoverPlanRevisionDO();
        CutoverPlanStorage.applyContent(row, JsonUtils.parseTree("{\"editMode\":\"ONLINE_TEMPLATE_SIMPLE_D\",\"scheduledTime\":1793498400000}"));
        assertThat(row.getScheduledTime()).isEqualTo(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(1793498400000L), java.time.ZoneId.systemDefault()));
        var projected = JsonUtils.getObjectMapper().createObjectNode();
        CutoverPlanStorage.appendScheduledTime(projected, row);
        assertThat(projected.path("scheduledTime").asLong()).isEqualTo(1793498400000L);
        CutoverPlanStorage.applyContent(row, JsonUtils.parseTree("{\"editMode\":\"ONLINE_TEMPLATE_SIMPLE_D\",\"scheduledTime\":null}"));
        assertThat(row.getScheduledTime()).isNull();
    }

    @Test
    void historicalSourceUsesItsExactResultAfterCurrentSelectionChanges() {
        var plan = plan();
        plan.setSourceSnapshot("旧原件不能作为新业务读取的后备来源");
        sources(plan);
        var source = storage.source(plan);
        assertThat(source.taskVersion()).isEqualTo(6);
        assertThat(source.failedRiskFacts()).singleElement().satisfies(risk -> {
            assertThat(risk.itemResultVersion()).isEqualTo(2);
            assertThat(risk.factDescription()).isEqualTo("原版本风险");
        });
        verify(results).selectById(91L);
        verify(results, never()).selectCurrentByChecklist(any());
        verify(results, never()).selectCurrentByChecklistForUpdate(any());
    }

    @Test
    void blankDraftStillPersistsEveryRiskReferenceBeforeMitigationIsFilled() {
        var plan = plan();
        sources(plan);
        var source = storage.source(plan);
        var assessed = CutoverAssessmentDeviceDO.builder().id(71L).deviceId(7L).build();
        when(assessments.selectListByParents(any())).thenReturn(List.of(assessed));
        when(results.selectByVersion(argThat(query -> Objects.equals(query.tenantId(), 1L)
                && Objects.equals(query.checklistItemId(), 8L) && Objects.equals(query.resultVersion(), 2)))).thenReturn(result());
        when(devices.insert(any(CutoverPlanDeviceDO.class))).thenReturn(1);
        when(risks.insert(any(CutoverPlanRiskMitigationDO.class))).thenReturn(1);
        var content = JsonUtils.parseTree("""
                {"editMode":"ONLINE_TEMPLATE_STANDARD","overview":{"deviceDetails":[]},"riskMitigations":[]}
                """);
        storage.replaceDraft(CutoverPlanStorage.Write.builder().plan(plan).source(source).content(content)
                .actorId(10L).time(LocalDateTime.of(2026, 10, 13, 20, 0)).build());
        var saved = ArgumentCaptor.forClass(CutoverPlanRiskMitigationDO.class);
        verify(risks).insert(saved.capture());
        assertThat(saved.getValue().getChecklistResultId()).isEqualTo(91);
        assertThat(saved.getValue().getChecklistResultVersion()).isEqualTo(2);
        assertThat(saved.getValue().getMitigation()).isNull();
        assertThat(saved.getValue().getTenantId()).isEqualTo(1);
    }

    @Test
    void submittedContentAndOtherTenantCannotBeReplaced() {
        var plan = plan();
        sources(plan);
        var source = storage.source(plan);
        plan.setStatusCode("SUBMITTED");
        var write = CutoverPlanStorage.Write.builder().plan(plan).source(source).actorId(10L).time(LocalDateTime.now()).build();
        assertThatThrownBy(() -> storage.replaceDraft(write)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(drafts);
        TenantContextHolder.setTenantId(2L);
        assertThatThrownBy(() -> storage.source(plan)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("租户");
    }

    @Test
    void currentContactProjectsLatestChangeWithoutMutatingApprovalBaseline() {
        var baseline = support();
        when(changes.selectById(40L)).thenReturn(CutoverPlanSupportContactChangeDO.builder()
                .id(40L).tenantId(1L).supportArrangementId(30L).changeNo(2).previousChangeId(39L)
                .afterPersonName("当前联系人").afterPhone("13800000000").afterArriveTime(LocalDateTime.of(2026, 10, 14, 9, 0))
                .afterCustomerContactId(602L).afterCustomerContactVersion(2L).build());
        baseline.setCustomerContactId(601L); baseline.setCustomerContactVersion(1L);
        var current = storage.currentSupport(baseline);
        assertThat(current.getPersonName()).isEqualTo("当前联系人");
        assertThat(current.getPhone()).isEqualTo("13800000000");
        assertThat(baseline.getPersonName()).isEqualTo("批准时联系人");
        assertThat(baseline.getPhone()).isEqualTo("13900000000");
        assertThat(current.getDutyDescription()).isEqualTo(baseline.getDutyDescription());
        assertThat(current.getCustomerContactId()).isEqualTo(602L);
        assertThat(CutoverPlanStorage.supportContent(current).path("customerContactReference").path("contactVersion").asLong()).isEqualTo(2L);
        assertThat(baseline.getCustomerContactId()).isEqualTo(601L);
        assertThat(baseline.getCustomerContactVersion()).isEqualTo(1L);
        assertThat(current).isNotSameAs(baseline);
    }

    @Test
    void contactPointerCannotExposeAnotherArrangementOrTenant() {
        var baseline = support();
        when(changes.selectById(40L)).thenReturn(CutoverPlanSupportContactChangeDO.builder()
                .id(40L).tenantId(1L).supportArrangementId(31L).build());
        assertThatThrownBy(() -> storage.currentSupport(baseline)).isInstanceOf(IllegalArgumentException.class);
        when(changes.selectById(40L)).thenReturn(CutoverPlanSupportContactChangeDO.builder()
                .id(40L).tenantId(2L).supportArrangementId(30L).build());
        assertThatThrownBy(() -> storage.currentSupport(baseline)).isInstanceOf(IllegalArgumentException.class);
    }

    private void sources(CutoverPlanRevisionDO plan) {
        var section = new CutoverConfigurationSaveReqVO.PlanTemplateSectionVO();
        section.setStableSectionKey("PRE_OPERATION"); section.setTitle("准备"); section.setSortOrder(1);
        section.setCutoverTypeCodes(List.of("VERSION_UPGRADE")); section.setLevelCodes(List.of("A")); section.setRequired(true);
        when(metadata.sectionViews(5L)).thenReturn(List.of(section));
        var device = CutoverPlanDeviceDO.builder().planRevisionId(plan.getId()).deviceId(7L).sn("SN-7")
                .projectAssignmentVersion(3L).sortOrder(0).build();
        when(devices.selectListByParents(any())).thenReturn(List.of(device));
        var risk = CutoverPlanRiskMitigationDO.builder().planRevisionId(plan.getId()).checklistResultId(91L)
                .checklistItemId(8L).checklistResultVersion(2).sortOrder(0).build(); risk.setTenantId(1L);
        when(risks.selectListByParents(any())).thenReturn(List.of(risk));
        when(results.selectById(91L)).thenReturn(result());
        var item = new CutoverChecklistItemDO(); item.setId(8L); item.setTenantId(1L); item.setChecklistId(3L);
        item.setStableItemKey("risk-1"); item.setItemName("原风险项"); when(items.selectById(8L)).thenReturn(item);
    }

    private static CutoverChecklistItemResultDO result() {
        var value = new CutoverChecklistItemResultDO(); value.setId(91L); value.setTenantId(1L);
        value.setChecklistItemId(8L); value.setResultVersion(2); value.setFactDescription("原版本风险"); return value;
    }
    private static CutoverPlanRevisionDO plan() {
        var value = new CutoverPlanRevisionDO(); value.setId(20L); value.setTenantId(1L); value.setCutoverTaskId(10L);
        value.setStatusCode("DRAFT"); value.setCurrentMarker(1); value.setSourceTaskVersion(6L);
        value.setAssessmentId(2L); value.setAssessmentVersion(1L); value.setGradeCode("A");
        value.setChecklistId(3L); value.setChecklistVersion(2L); value.setSourceProjectId(4L);
        value.setSourceProjectVersion(2L); value.setSourceProjectScopeVersion(8L); value.setSourceCutoverTypeCode("VERSION_UPGRADE");
        value.setConfigurationRevisionId(5L); value.setConfigurationCode("CUT"); value.setConfigurationRevisionNo(1);
        return value;
    }
    private static CutoverSupportArrangementDO support() {
        var value = new CutoverSupportArrangementDO(); value.setId(30L); value.setTenantId(1L);
        value.setRoleCode("CUSTOMER"); value.setPersonName("批准时联系人"); value.setPhone("13900000000");
        value.setDutyDescription("保障职责"); value.setCurrentSupportContactChangeId(40L); return value;
    }
}
