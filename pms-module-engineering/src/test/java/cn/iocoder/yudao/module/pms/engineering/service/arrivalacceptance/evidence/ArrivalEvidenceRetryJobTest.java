package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalEvidenceRetryJobTest {

    @Mock ArrivalEvidenceRetryService retryService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void processesDueEvidenceUntilNoMoreRows() {
        TenantContextHolder.setTenantId(7L);
        when(retryService.retryNext(any())).thenReturn(true, true, false);

        String result = new ArrivalEvidenceRetryJob(
                retryService, new MockEnvironment()).execute(null);

        assertEquals("到货签收证据业务重试处理 2 条", result);
    }
}
