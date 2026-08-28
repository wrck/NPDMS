package cn.iocoder.yudao.module.pms.service.api.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask.SrvTaskMapper;
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
class InspectionGovernanceGuardProviderTest {

    private static final long TENANT_ID = 7L;
    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 25, 15, 0);

    @Mock
    private SrvTaskMapper taskMapper;
    private InspectionGovernanceGuardProvider provider;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        provider = new InspectionGovernanceGuardProvider(taskMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldBlockNonTerminalAndUnknownButAllowTerminalTasks() {
        when(taskMapper.selectListForGovernanceGuard(any())).thenReturn(List.of(
                task(1L, 201L, 3, 1), task(1L, 202L, 4, 1), task(2L, 203L, 99, 1)));

        ProjectGovernanceProviderFact fact = provider.inspect(query(Set.of(2L, 1L)));

        assertEquals("INSPECTION", fact.provider());
        assertEquals(List.of("201", "203"), fact.blockers().stream().map(blocker -> blocker.objectId()).toList());
        assertEquals("UNKNOWN", fact.blockers().getLast().status());
    }

    @Test
    void shouldReturnEmptyFactWithoutQueryAndRejectCrossTenantQuery() {
        ProjectGovernanceProviderFact empty = provider.inspect(query(Set.of()));

        assertEquals("EMPTY", empty.watermark());
        assertTrue(empty.blockers().isEmpty());
        verifyNoInteractions(taskMapper);
        assertThrows(IllegalArgumentException.class, () -> provider.inspect(
                new ProjectGovernanceGuardQuery(8L, Set.of(1L), "REOPEN", CHECKED_AT)));
    }

    @Test
    void shouldRejectOutOfScopePersistenceFact() {
        SrvTaskDO task = task(1L, 201L, 4, 1);
        task.setProjectId(2L);
        when(taskMapper.selectListForGovernanceGuard(any())).thenReturn(List.of(task));

        assertThrows(IllegalStateException.class, () -> provider.inspect(query(Set.of(1L))));
    }

    @Test
    void shouldKeepDigestStableAcrossOrderAndChangeWatermarkWithVersion() {
        SrvTaskDO first = task(1L, 201L, 4, 1);
        SrvTaskDO second = task(2L, 202L, 5, 1);
        SrvTaskDO changed = task(1L, 201L, 4, 2);
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
        return new ProjectGovernanceGuardQuery(TENANT_ID, projectIds, "REOPEN", CHECKED_AT);
    }

    private static SrvTaskDO task(Long projectId, Long id, Integer status, Integer version) {
        SrvTaskDO task = new SrvTaskDO();
        task.setTenantId(TENANT_ID);
        task.setProjectId(projectId);
        task.setId(id);
        task.setStatus(status);
        task.setVersion(version);
        task.setUpdateTime(CHECKED_AT.plusMinutes(id));
        return task;
    }
}
