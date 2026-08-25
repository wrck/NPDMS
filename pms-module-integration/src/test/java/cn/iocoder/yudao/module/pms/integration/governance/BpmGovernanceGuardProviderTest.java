package cn.iocoder.yudao.module.pms.integration.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BpmGovernanceGuardProviderTest {

    private static final long TENANT_ID = 7L;
    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 25, 16, 0);

    private RuntimeService runtimeService;
    private ProcessInstanceQuery instanceQuery;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        runtimeService = mock(RuntimeService.class);
        instanceQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processDefinitionKey("project-progress-policy")).thenReturn(instanceQuery);
        when(instanceQuery.processInstanceTenantId("7")).thenReturn(instanceQuery);
        when(instanceQuery.active()).thenReturn(instanceQuery);
        when(instanceQuery.includeProcessVariables()).thenReturn(instanceQuery);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldBlockOnlyActiveInstancesForCandidateProjects() {
        ProcessInstance candidate = instance("p-1", "7", 11L);
        ProcessInstance unrelated = instance("p-2", "7", 12L);
        when(instanceQuery.list()).thenReturn(List.of(candidate, unrelated));

        ProjectGovernanceProviderFact fact = provider().inspect(query(Set.of(11L)));

        assertEquals("BPM_APPROVAL", fact.provider());
        assertEquals(1, fact.blockers().size());
        assertEquals("p-1", fact.blockers().getFirst().objectId());
        assertEquals("ACTIVE_BPM_APPROVAL", fact.blockers().getFirst().code());
        verify(instanceQuery).active();
    }

    @Test
    void shouldFailClosedForMissingInvalidOrCrossTenantAssociation() {
        ProcessInstance missing = instance("p-1", "7", null);
        ProcessInstance invalid = instance("p-2", "7", null);
        when(invalid.getProcessVariables()).thenReturn(Map.of("projectId", 11));
        ProcessInstance crossTenant = instance("p-3", "8", 11L);
        when(instanceQuery.list()).thenReturn(List.of(missing, invalid, crossTenant));

        ProjectGovernanceProviderFact fact = provider().inspect(query(Set.of(11L)));

        assertEquals(3, fact.blockers().size());
        assertEquals(Set.of("BPM_ASSOCIATION_UNKNOWN"),
                fact.blockers().stream().map(blocker -> blocker.code()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldKeepFactsStableAcrossOrderAndChangeWhenInstanceEnds() {
        ProcessInstance first = instance("p-1", "7", 11L);
        ProcessInstance second = instance("p-2", "7", 11L);
        when(instanceQuery.list()).thenReturn(List.of(first, second), List.of(second, first), List.of(first));

        ProjectGovernanceProviderFact before = provider().inspect(query(Set.of(11L)));
        ProjectGovernanceProviderFact reordered = provider().inspect(query(Set.of(11L)));
        ProjectGovernanceProviderFact after = provider().inspect(query(Set.of(11L)));

        assertEquals(before.factDigest(), reordered.factDigest());
        assertEquals(before.watermark(), reordered.watermark());
        assertNotEquals(before.factDigest(), after.factDigest());
        assertNotEquals(before.watermark(), after.watermark());
    }

    @Test
    void shouldShortCircuitEmptyProjectsRejectCrossTenantAndFailClosedWhenUnconfigured() {
        RuntimeService emptyRuntime = mock(RuntimeService.class);
        ProjectGovernanceProviderFact empty = new BpmGovernanceGuardProvider(
                emptyRuntime, "project-progress-policy").inspect(query(Set.of()));
        assertEquals("EMPTY", empty.watermark());
        verifyNoInteractions(emptyRuntime);
        assertThrows(IllegalArgumentException.class, () -> provider().inspect(
                new ProjectGovernanceGuardQuery(8L, Set.of(11L), "REOPEN", CHECKED_AT)));

        ProjectGovernanceProviderFact unavailable = new BpmGovernanceGuardProvider(runtimeService, " ")
                .inspect(query(Set.of(11L)));
        assertEquals("PROVIDER_UNAVAILABLE", unavailable.blockers().getFirst().code());

        RuntimeService failedRuntime = mock(RuntimeService.class);
        when(failedRuntime.createProcessInstanceQuery()).thenThrow(new IllegalStateException("timeout"));
        ProjectGovernanceProviderFact failed = new BpmGovernanceGuardProvider(
                failedRuntime, "project-progress-policy").inspect(query(Set.of(11L)));
        assertEquals("PROVIDER_UNAVAILABLE", failed.blockers().getFirst().code());
    }

    private BpmGovernanceGuardProvider provider() {
        return new BpmGovernanceGuardProvider(runtimeService, "project-progress-policy");
    }

    private static ProjectGovernanceGuardQuery query(Set<Long> projectIds) {
        return new ProjectGovernanceGuardQuery(TENANT_ID, projectIds, "EXCEPTION_CLOSE", CHECKED_AT);
    }

    private static ProcessInstance instance(String id, String tenantId, Long projectId) {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn(id);
        when(instance.getTenantId()).thenReturn(tenantId);
        when(instance.getProcessDefinitionId()).thenReturn("project-progress-policy:3");
        when(instance.getProcessDefinitionVersion()).thenReturn(3);
        when(instance.getStartTime()).thenReturn(new Date(1_700_000_000_000L + id.hashCode()));
        when(instance.getBusinessStatus()).thenReturn("RUNNING");
        when(instance.getProcessVariables()).thenReturn(projectId == null ? Map.of() :
                Map.of("projectId", projectId, "PROCESS_STATUS", 1));
        return instance;
    }
}
