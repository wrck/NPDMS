package cn.iocoder.yudao.module.pms.project.service.projectgovernance.provider;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask.ProjectTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskGovernanceGuardProviderTest {

    private static final long TENANT_ID = 7L;
    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Mock
    private ProjectTaskMapper taskMapper;
    private ProjectTaskGovernanceGuardProvider provider;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        provider = new ProjectTaskGovernanceGuardProvider(taskMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnStableFactAndOnlyBlockNonTerminalTasks() {
        ProjectTaskDO pending = task(2L, 101L, "T-PENDING", 2, 3);
        ProjectTaskDO completed = task(1L, 100L, "T-DONE", 5, 4);
        when(taskMapper.selectListForGovernanceGuard(any())).thenReturn(List.of(pending, completed))
                .thenReturn(List.of(completed, pending));

        ProjectGovernanceGuardQuery query = query(Set.of(2L, 1L));
        ProjectGovernanceProviderFact first = provider.inspect(query);
        ProjectGovernanceProviderFact second = provider.inspect(query);

        assertEquals(first.factDigest(), second.factDigest());
        assertEquals(first.watermark(), second.watermark());
        assertEquals("PROJECT_TASK_V1", first.factVersion());
        assertEquals(1, first.blockers().size());
        assertEquals("101", first.blockers().getFirst().objectId());
        assertEquals("IN_PROGRESS", first.blockers().getFirst().status());
        assertEquals("项目任务阻断", first.blockers().getFirst().summary());
    }

    @Test
    void shouldShortCircuitEmptyProjectsAndRejectCrossTenantQuery() {
        ProjectGovernanceProviderFact empty = provider.inspect(query(Set.of()));

        assertEquals("EMPTY", empty.watermark());
        assertTrue(empty.blockers().isEmpty());
        verifyNoInteractions(taskMapper);
        assertThrows(IllegalArgumentException.class, () -> provider.inspect(
                new ProjectGovernanceGuardQuery(8L, Set.of(1L), "EXCEPTION_CLOSE", CHECKED_AT)));
    }

    @Test
    void shouldRejectOutOfScopeFactsReturnedByPersistence() {
        ProjectTaskDO crossTenant = task(1L, 100L, "T-100", 5, 1);
        crossTenant.setTenantId(8L);
        when(taskMapper.selectListForGovernanceGuard(any())).thenReturn(List.of(crossTenant));

        assertThrows(IllegalStateException.class, () -> provider.inspect(query(Set.of(1L))));
    }

    @Test
    void shouldChangeFrozenFactWhenTaskVersionChanges() {
        ProjectTaskDO before = task(1L, 100L, "T-100", 5, 1);
        ProjectTaskDO after = task(1L, 100L, "T-100", 5, 2);
        after.setUpdateTime(before.getUpdateTime().plusSeconds(1));
        when(taskMapper.selectListForGovernanceGuard(any())).thenReturn(List.of(before), List.of(after));

        ProjectGovernanceProviderFact first = provider.inspect(query(Set.of(1L)));
        ProjectGovernanceProviderFact second = provider.inspect(query(Set.of(1L)));

        assertTrue(!first.watermark().equals(second.watermark()));
        assertTrue(!first.factDigest().equals(second.factDigest()));
    }

    @Test
    void queryMustOwnAnImmutableSortedProjectSet() {
        ProjectGovernanceGuardQuery query = query(Set.of(3L, 1L, 2L));

        assertEquals(List.of(1L, 2L, 3L), List.copyOf(query.projectIds()));
        assertThrows(UnsupportedOperationException.class, () -> query.projectIds().add(4L));
    }

    @Test
    void queryMustRejectUnknownAction() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProjectGovernanceGuardQuery(TENANT_ID, Set.of(1L), "UNKNOWN", CHECKED_AT));
    }

    private static ProjectGovernanceGuardQuery query(Set<Long> projectIds) {
        return new ProjectGovernanceGuardQuery(TENANT_ID, projectIds, "EXCEPTION_CLOSE", CHECKED_AT);
    }

    private static ProjectTaskDO task(Long projectId, Long id, String code, Integer status, Integer version) {
        ProjectTaskDO task = new ProjectTaskDO();
        task.setTenantId(TENANT_ID);
        task.setProjectId(projectId);
        task.setId(id);
        task.setCode(code);
        task.setStatus(status);
        task.setVersion(version);
        task.setUpdateTime(CHECKED_AT.plusMinutes(id));
        return task;
    }
}
