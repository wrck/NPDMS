package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectContractProjectionJobTest {
    @Test
    void failedTenantIsReportedAndOtherTenantsStillRun() {
        var service = mock(ProjectContractProjectionService.class);
        var tenants = mock(TenantFrameworkService.class);
        when(tenants.getTenantIds()).thenReturn(List.of(1L, 2L));
        when(service.reconcile()).thenAnswer(call -> {
            if (TenantContextHolder.getRequiredTenantId() == 1L) throw new IllegalStateException("failed");
            return 3;
        });
        var job = new ProjectContractProjectionJob(service, tenants);
        assertThrows(IllegalStateException.class, () -> job.execute(""));
        verify(service, times(2)).reconcile();
        assertNull(TenantContextHolder.getTenantId());
    }
}
