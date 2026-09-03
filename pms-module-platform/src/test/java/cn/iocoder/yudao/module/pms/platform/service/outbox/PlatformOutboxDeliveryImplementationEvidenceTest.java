package cn.iocoder.yudao.module.pms.platform.service.outbox;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.PlatformOutboxDeliveryMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.DueOutboxListQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformOutboxDeliveryImplementationEvidenceTest {

    @Mock
    private PlatformOutboxDeliveryMapper mapper;
    @InjectMocks
    private PlatformOutboxDeliveryApiImpl service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void claimDueSupportsImplementationEvidencePublished() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 9, 3, 12, 0);
        when(mapper.selectDueForUpdate(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service.claimDue(new PlatformOutboxClaimQuery(
                "ImplementationEvidencePublished", dueAt, 10));

        verify(mapper).selectDueForUpdate(new DueOutboxListQuery(
                7L, Set.of("ImplementationEvidencePublished"), dueAt, 10));
    }
}
