package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.ProjectSystemQualificationFactApi;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationLockQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSystemQualificationApiAdapterTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void locksFixedActiveS4FactAndMapsCurrentVersions() {
        TenantContextHolder.setTenantId(7L);
        ProjectSystemQualificationFactApi api = mock(ProjectSystemQualificationFactApi.class);
        when(api.lockCurrentForSystem(new ProjectSystemQualificationLockQuery(100L, "ACTIVE", "S4")))
                .thenReturn(new ProjectSystemQualificationFact(
                        100L, 8L, "ACTIVE", "S4", 11, 12L, 13L));

        var fact = new ProjectSystemQualificationApiAdapter(api).lockCurrent(7L, 100L);

        assertEquals(100L, fact.projectId());
        assertEquals(8L, fact.currentManagerUserId());
        assertEquals(11, fact.projectVersion());
        assertEquals(12L, fact.participantFactVersion());
        assertEquals(13L, fact.treeVersion());
        ArgumentCaptor<ProjectSystemQualificationLockQuery> query =
                ArgumentCaptor.forClass(ProjectSystemQualificationLockQuery.class);
        verify(api).lockCurrentForSystem(query.capture());
        assertEquals("ACTIVE", query.getValue().requiredLifecycleStatus());
        assertEquals("S4", query.getValue().requiredCurrentStage());
    }

    @Test
    void rejectsMissingOrMismatchedTrustedTenantBeforeOwnerCall() {
        ProjectSystemQualificationFactApi api = mock(ProjectSystemQualificationFactApi.class);
        ProjectSystemQualificationApiAdapter adapter = new ProjectSystemQualificationApiAdapter(api);

        assertThrows(IllegalStateException.class, () -> adapter.lockCurrent(7L, 100L));
        TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalStateException.class, () -> adapter.lockCurrent(7L, 100L));

        verify(api, never()).lockCurrentForSystem(new ProjectSystemQualificationLockQuery(100L, "ACTIVE", "S4"));
    }

    @Test
    void rejectsOwnerIdentityOrEligibilityMismatch() {
        TenantContextHolder.setTenantId(7L);
        ProjectSystemQualificationFactApi api = mock(ProjectSystemQualificationFactApi.class);
        ProjectSystemQualificationLockQuery query = new ProjectSystemQualificationLockQuery(100L, "ACTIVE", "S4");
        when(api.lockCurrentForSystem(query)).thenReturn(new ProjectSystemQualificationFact(
                101L, 8L, "ACTIVE", "S4", 11, 12L, 13L));

        assertThrows(IllegalStateException.class,
                () -> new ProjectSystemQualificationApiAdapter(api).lockCurrent(7L, 100L));
    }
}
