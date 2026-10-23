package cn.iocoder.yudao.module.pms.cutover.api.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CutoverGovernanceGuardProviderTest {

    private static final long TENANT_ID = 7L;
    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 25, 15, 0);

    @Mock
    private CutoverTaskMapper taskMapper;
    private CutoverGovernanceGuardProvider provider;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        provider = new CutoverGovernanceGuardProvider(taskMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldBlockNonTerminalAndUnknownButAllowTerminalTasks() {
        when(taskMapper.selectListForGovernanceGuard(any())).thenReturn(List.of(
                task(1L, 101L, "SURVEYING", 1L), task(1L, 102L, "ARCHIVED", 1L), task(2L, 103L, null, 1L)));

        ProjectGovernanceProviderFact fact = provider.inspect(query(Set.of(2L, 1L)));

        assertEquals("CUTOVER", fact.provider());
        assertEquals(List.of("101", "103"), fact.blockers().stream().map(blocker -> blocker.objectId()).toList());
        assertEquals("UNKNOWN", fact.blockers().getLast().status());
    }

    @Test
    void shouldReturnEmptyFactWithoutQueryAndRejectCrossTenantQuery() {
        ProjectGovernanceProviderFact empty = provider.inspect(query(Set.of()));

        assertEquals("EMPTY", empty.watermark());
        assertTrue(empty.blockers().isEmpty());
        verifyNoInteractions(taskMapper);
        assertThrows(IllegalArgumentException.class, () -> provider.inspect(
                new ProjectGovernanceGuardQuery(8L, Set.of(1L), "ROLLBACK", CHECKED_AT)));
    }

    @Test
    void shouldRejectOutOfScopePersistenceFact() {
        CutoverTaskDO task = task(1L, 101L, "ARCHIVED", 1L);
        task.setTenantId(8L);
        when(taskMapper.selectListForGovernanceGuard(any())).thenReturn(List.of(task));

        assertThrows(IllegalStateException.class, () -> provider.inspect(query(Set.of(1L))));
    }

    @Test
    void shouldKeepDigestStableAcrossOrderAndChangeWatermarkWithVersion() {
        CutoverTaskDO first = task(1L, 101L, "ARCHIVED", 1L);
        CutoverTaskDO second = task(2L, 102L, "ARCHIVED", 1L);
        CutoverTaskDO changed = task(1L, 101L, "ARCHIVED", 2L);
        changed.setUpdateTime(first.getUpdateTime().plusSeconds(1));
        when(taskMapper.selectListForGovernanceGuard(any()))
                .thenReturn(List.of(first, second), List.of(second, first), List.of(changed, second));

        ProjectGovernanceProviderFact before = provider.inspect(query(Set.of(1L, 2L)));
        ProjectGovernanceProviderFact reordered = provider.inspect(query(Set.of(1L, 2L)));
        ProjectGovernanceProviderFact after = provider.inspect(query(Set.of(1L, 2L)));

        assertEquals(before.factDigest(), reordered.factDigest());
        assertEquals(before.watermark(), reordered.watermark());
        assertNotEquals(before.factDigest(), after.factDigest());
        assertNotEquals(before.watermark(), after.watermark());
    }

    private static ProjectGovernanceGuardQuery query(Set<Long> projectIds) {
        return new ProjectGovernanceGuardQuery(TENANT_ID, projectIds, "ROLLBACK", CHECKED_AT);
    }

    private static CutoverTaskDO task(Long projectId, Long id, String status, Long version) {
        CutoverTaskDO task = new CutoverTaskDO();
        task.setTenantId(TENANT_ID);
        task.setProjectId(projectId);
        task.setId(id);
        task.setTaskStatus(status);
        task.setVersion(version);
        task.setUpdateTime(CHECKED_AT.plusMinutes(id));
        return task;
    }
}
