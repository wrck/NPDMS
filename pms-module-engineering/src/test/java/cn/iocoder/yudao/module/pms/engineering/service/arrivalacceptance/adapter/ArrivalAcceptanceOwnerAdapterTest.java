package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArrivalAcceptanceOwnerAdapterTest {

    @Test
    void inspectsParticipantAndEditScopeWithoutTreatingScopeAsProjectRole() {
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        when(participantApi.inspect(any())).thenReturn(participant("S4", 5, 5L));
        when(scopeApi.resolveCurrent(any())).thenReturn(scope(9L, Set.of(100L)));
        ProjectQualificationPort adapter = new ProjectQualificationApiAdapter(participantApi, scopeApi);

        ProjectQualificationPort.ProjectQualificationFact fact = adapter.inspect(1L, 100L, 7L);

        assertEquals("S4", fact.currentStage());
        assertEquals(Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), fact.effectiveRoleCodes());
        assertEquals(9L, fact.scopeVersion());
        ArgumentCaptor<ProjectParticipantFactQuery> participantQuery =
                ArgumentCaptor.forClass(ProjectParticipantFactQuery.class);
        verify(participantApi).inspect(participantQuery.capture());
        assertEquals(100L, participantQuery.getValue().projectId());
        assertEquals(7L, participantQuery.getValue().subjectUserId());
        assertEquals(ProjectQualificationApiAdapter.SUPPORTED_PROJECT_ROLES,
                participantQuery.getValue().requiredRoleCodes());
        ArgumentCaptor<ProjectCurrentScopeQuery> scopeQuery =
                ArgumentCaptor.forClass(ProjectCurrentScopeQuery.class);
        verify(scopeApi).resolveCurrent(scopeQuery.capture());
        assertEquals(ProjectScopeApi.ACTION_EDIT, scopeQuery.getValue().actionCode());
    }

    @Test
    void locksActiveProjectWithNullStageThenChecksS4FactAndScopeVersions() {
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        when(participantApi.lockAndRevalidate(any())).thenReturn(participant("S4", 5, 5L));
        when(scopeApi.lockAndRevalidate(any())).thenReturn(scope(9L, Set.of(100L)));
        ProjectQualificationPort adapter = new ProjectQualificationApiAdapter(participantApi, scopeApi);

        ProjectQualificationPort.ProjectQualificationFact fact = adapter.lockAndRevalidate(
                new ProjectQualificationPort.RevalidationCommand(
                        1L, 100L, 7L, 5, 5L, 9L, true));

        assertEquals(5L, fact.factVersion());
        ArgumentCaptor<ProjectParticipantFactRevalidationQuery> participantQuery =
                ArgumentCaptor.forClass(ProjectParticipantFactRevalidationQuery.class);
        verify(participantApi).lockAndRevalidate(participantQuery.capture());
        assertEquals("ACTIVE", participantQuery.getValue().requiredLifecycleStatus());
        assertEquals(null, participantQuery.getValue().requiredCurrentStage());
        assertEquals(Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                participantQuery.getValue().requiredRoleCodes());
        ArgumentCaptor<ProjectScopeRevalidationQuery> scopeQuery =
                ArgumentCaptor.forClass(ProjectScopeRevalidationQuery.class);
        verify(scopeApi).lockAndRevalidate(scopeQuery.capture());
        assertEquals(ProjectScopeApi.ACTION_EDIT, scopeQuery.getValue().actionCode());
        assertEquals(9L, scopeQuery.getValue().expectedScopeVersion());
    }

    @Test
    void staleParticipantFactFailsClosedBeforeScopeLock() {
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        when(participantApi.lockAndRevalidate(any())).thenReturn(participant("S3", 5, 6L));
        ProjectQualificationPort adapter = new ProjectQualificationApiAdapter(participantApi, scopeApi);

        assertThrows(IllegalStateException.class, () -> adapter.lockAndRevalidate(
                new ProjectQualificationPort.RevalidationCommand(
                        1L, 100L, 7L, 5, 5L, 9L, true)));

        verify(scopeApi, never()).lockAndRevalidate(any());
    }

    @Test
    void projectOutsideEditScopeFailsClosed() {
        ProjectParticipantFactApi participantApi = mock(ProjectParticipantFactApi.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        when(participantApi.inspect(any())).thenReturn(participant("S4", 5, 5L));
        when(scopeApi.resolveCurrent(any())).thenReturn(scope(9L, Set.of(200L)));
        ProjectQualificationPort adapter = new ProjectQualificationApiAdapter(participantApi, scopeApi);

        assertThrows(IllegalStateException.class, () -> adapter.inspect(1L, 100L, 7L));
    }

    @Test
    void fileAdapterUsesReadOnlyReferenceSetContracts() {
        FileArtifactApi fileApi = mock(FileArtifactApi.class);
        FileReferenceSetKey key = new FileReferenceSetKey(
                "IMP", "ARRIVAL_ACCEPTANCE", "100", "RECEIPT");
        FileReferenceSetFact fact = new FileReferenceSetFact(key, 3L, List.of());
        when(fileApi.inspectReferenceSets(any())).thenReturn(List.of(fact));
        when(fileApi.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(fact));
        FileArtifactFactPort adapter = new FileArtifactApiAdapter(fileApi);

        assertEquals(List.of(fact), adapter.inspectReferenceSets(List.of(key)));
        assertEquals(List.of(fact), adapter.lockAndRevalidateReferenceSets(
                List.of(new FileReferenceSetExpectation(key, 3L, List.of()))));

        ArgumentCaptor<FileReferenceSetCollectionQuery> inspectQuery =
                ArgumentCaptor.forClass(FileReferenceSetCollectionQuery.class);
        verify(fileApi).inspectReferenceSets(inspectQuery.capture());
        assertEquals(FileActionCodes.READ, inspectQuery.getValue().requiredAction());
        ArgumentCaptor<FileReferenceSetCollectionRevalidationQuery> revalidationQuery =
                ArgumentCaptor.forClass(FileReferenceSetCollectionRevalidationQuery.class);
        verify(fileApi).lockAndRevalidateReferenceSets(revalidationQuery.capture());
        assertEquals(FileActionCodes.READ, revalidationQuery.getValue().requiredAction());
    }

    private static ProjectParticipantFact participant(String stage, Integer projectVersion, Long factVersion) {
        return new ProjectParticipantFact(100L, 7L,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY",
                "ACTIVE", stage, projectVersion, factVersion);
    }

    private static ProjectScopeResult scope(Long version, Set<Long> fullProjectIds) {
        return new ProjectScopeResult(100L, version, fullProjectIds, Set.of());
    }
}
