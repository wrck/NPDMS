package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeAcceptanceLockCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeVersionLockRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryScopeAcceptanceLockApiImplTest {

    @Mock
    private DeliveryScopeMapper deliveryScopeMapper;

    private DeliveryScopeAcceptanceLockApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        api = new DeliveryScopeAcceptanceLockApiImpl(deliveryScopeMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnCurrentVersionsInStableScopeOrder() {
        when(deliveryScopeMapper.selectCurrentVersionsForAcceptanceLock(any())).thenReturn(List.of(
                row(301L, 4L), row(302L, 7L)));

        var result = api.lockCurrentByProject(new DeliveryScopeAcceptanceLockCommand(
                1L, 101L, 201L, "op-stage"));

        assertEquals(List.of(301L, 302L), result.stream().map(fact -> fact.deliveryScopeId()).toList());
        assertEquals(List.of(4L, 7L), result.stream().map(fact -> fact.allocationVersion()).toList());
    }

    @Test
    void shouldAcceptProjectWithNoCurrentScope() {
        when(deliveryScopeMapper.selectCurrentVersionsForAcceptanceLock(any())).thenReturn(List.of());

        assertEquals(List.of(), api.lockCurrentByProject(new DeliveryScopeAcceptanceLockCommand(
                1L, 101L, 201L, "op-empty")));
    }

    @Test
    void shouldRejectCrossTenantOrUnstableMapperResult() {
        assertThrows(RuntimeException.class, () -> api.lockCurrentByProject(
                new DeliveryScopeAcceptanceLockCommand(2L, 101L, 201L, "op-cross-tenant")));

        when(deliveryScopeMapper.selectCurrentVersionsForAcceptanceLock(any())).thenReturn(List.of(
                row(302L, 7L), row(301L, 4L)));
        assertThrows(RuntimeException.class, () -> api.lockCurrentByProject(
                new DeliveryScopeAcceptanceLockCommand(1L, 101L, 201L, "op-order")));
    }

    @Test
    void shouldRequireMandatoryCallerTransaction() throws Exception {
        Transactional transactional = DeliveryScopeAcceptanceLockApiImpl.class
                .getMethod("lockCurrentByProject", DeliveryScopeAcceptanceLockCommand.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    private DeliveryScopeVersionLockRow row(Long scopeId, Long allocationVersion) {
        DeliveryScopeVersionLockRow row = new DeliveryScopeVersionLockRow();
        row.setDeliveryScopeId(scopeId);
        row.setAllocationVersion(allocationVersion);
        return row;
    }
}
