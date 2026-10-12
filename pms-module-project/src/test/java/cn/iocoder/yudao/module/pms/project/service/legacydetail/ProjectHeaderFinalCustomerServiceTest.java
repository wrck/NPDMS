package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacySourceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectHeaderFinalCustomerServiceTest {
    private final ProjectManualCreationService access = mock(ProjectManualCreationService.class);
    private final ProjectLegacyDetailMapper sources = mock(ProjectLegacyDetailMapper.class);
    private final ProjectPartyMapper parties = mock(ProjectPartyMapper.class);
    private final ProjectHeaderFinalCustomerService service = new ProjectHeaderFinalCustomerService(access, sources, parties);
    private final ProjectAccessActor actor = new ProjectAccessActor(1L, 7L);
    private ProjectLegacySourceDO binding;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        binding = new ProjectLegacySourceDO();
        binding.setTenantId(1L); binding.setProjectId(100L); binding.setSourceProjectKey("301");
        binding.setCurrentSnapshotId(42L);
        when(sources.selectSourceForUpdate(any())).thenReturn(binding);
        when(parties.insert(any(ProjectPartyDO.class))).thenReturn(1);
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }

    private ProjectLegacyRecordDO header(Map<String, String> extras) {
        var values = new HashMap<>(Map.of("projectId", "301", "disabled", "0", "column013", "产品实际用户",
                "effectiveFrom", "2023-01-01 00:00:00.000"));
        values.putAll(extras);
        var row = new ProjectLegacyRecordDO(); row.setSourceTable("pm_project_header");
        row.setSourceKey("301"); row.setSnapshotId(42L); row.setPayloadJson(JsonUtils.toJsonString(values));
        return row;
    }

    @Test void actualEndUserBecomesFormalPartyWithoutGuessingCustomerCode() {
        var outcome = service.importHeader(100L, header(Map.of()), actor);
        var captor = ArgumentCaptor.forClass(ProjectPartyDO.class);
        verify(parties).insert(captor.capture());
        var party = captor.getValue();
        assertEquals("proj_project_party", outcome.targetTable());
        assertEquals("PARTY", outcome.targetRole()); assertEquals(0, outcome.targetSequence());
        assertEquals(party.getId(), outcome.targetId());
        assertEquals(1L, party.getTenantId()); assertEquals(100L, party.getProjectId());
        assertEquals("FINAL_CUSTOMER", party.getPartyRole());
        assertEquals("产品实际用户", party.getPartyName()); assertNull(party.getPartyCode());
        assertEquals("pm_project_header", party.getSourceTable()); assertEquals("301", party.getSourceRecordKey());
        assertEquals(LocalDateTime.of(2023, 1, 1, 0, 0), party.getEffectiveFrom());
        assertEquals("ACTIVE", party.getStatus());
        verify(access).getProjectForManage(100L, actor);
    }

    @Test void exactReplayReturnsSameBusinessPartyWithoutAnotherInsert() {
        var existing = new ProjectPartyDO(); existing.setId(200L); existing.setProjectId(100L);
        existing.setPartyName("产品实际用户"); existing.setStatus("ACTIVE");
        existing.setEffectiveFrom(LocalDateTime.of(2023, 1, 1, 0, 0));
        when(parties.selectSourceForUpdate(any())).thenReturn(existing);
        assertEquals(200L, service.importHeader(100L, header(Map.of()), actor).targetId());
        verify(parties, never()).insert(any(ProjectPartyDO.class));
    }

    @Test void existingRemovedOrConflictingPartyIsNeverResurrectedOrOverwritten() {
        var existing = new ProjectPartyDO(); existing.setId(200L); existing.setProjectId(100L);
        existing.setPartyName("另一个已确认用户"); existing.setStatus("ACTIVE");
        existing.setEffectiveFrom(LocalDateTime.of(2023, 1, 1, 0, 0));
        when(parties.selectSourceForUpdate(any())).thenReturn(existing);
        assertThrows(IllegalArgumentException.class, () -> service.importHeader(100L, header(Map.of()), actor));
        existing.setPartyName("产品实际用户"); existing.setDeleted(true);
        assertThrows(IllegalArgumentException.class, () -> service.importHeader(100L, header(Map.of()), actor));
        verify(parties, never()).insert(any(ProjectPartyDO.class));
        verify(parties, never()).updateById(any(ProjectPartyDO.class));
    }

    @Test void changedProjectOrSnapshotBindingFailsBeforeLookingUpParties() {
        binding.setProjectId(101L);
        assertThrows(IllegalArgumentException.class, () -> service.importHeader(100L, header(Map.of()), actor));
        binding.setProjectId(100L); binding.setCurrentSnapshotId(43L);
        assertThrows(IllegalArgumentException.class, () -> service.importHeader(100L, header(Map.of()), actor));
        verifyNoInteractions(parties);
    }

    @Test void foreignTenantAndDeniedProjectNeverReachSourceOrPartyData() {
        assertThrows(IllegalArgumentException.class, () -> service.importHeader(100L, header(Map.of()), new ProjectAccessActor(2L, 7L)));
        doThrow(new IllegalArgumentException("denied")).when(access).getProjectForManage(100L, actor);
        assertThrows(IllegalArgumentException.class, () -> service.importHeader(100L, header(Map.of()), actor));
        verifyNoInteractions(sources, parties);
    }

    @Test void absentFinalUserDoesNotCreateAMadeUpParty() {
        assertNull(service.importHeader(100L, header(Map.of("column013", "")), actor));
        verifyNoInteractions(parties);
    }

    @Test void disabledProjectStillRetainsItsActualEndUserBusinessFact() {
        var outcome = service.importHeader(100L, header(Map.of("disabled", "1")), actor);
        assertEquals("proj_project_party", outcome.targetTable());
        verify(parties).insert(any(ProjectPartyDO.class));
    }

    @Test void insertFailureCannotBecomeASuccessfulMapping() {
        when(parties.insert(any(ProjectPartyDO.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.importHeader(100L, header(Map.of()), actor));
    }

    @Test void readingFinalUsersUsesTheFormalPartyOwnerAndPermissionScope() {
        var party = new ProjectPartyDO(); party.setPartyName("产品实际用户");
        when(parties.selectHeaderFinalCustomers(any())).thenReturn(List.of(party));
        assertEquals(List.of("产品实际用户"), service.readNames(100L, actor));
        var query = ArgumentCaptor.forClass(ProjectPartyMapper.HeaderFinalCustomerQuery.class);
        verify(parties).selectHeaderFinalCustomers(query.capture());
        assertEquals(1L, query.getValue().tenantId()); assertEquals(100L, query.getValue().projectId());
        verify(access).getProject(100L, actor);
    }
}
