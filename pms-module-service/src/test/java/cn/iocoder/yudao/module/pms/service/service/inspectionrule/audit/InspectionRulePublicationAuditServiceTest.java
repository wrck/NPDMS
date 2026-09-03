package cn.iocoder.yudao.module.pms.service.service.inspectionrule.audit;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InspectionRulePublicationAuditServiceTest {

    @Mock
    private OperationAuditApi operationAuditApi;

    @Test
    void shouldRecordRejectedAuditInIndependentTransaction() throws Exception {
        InspectionRulePublicationAuditService service = new InspectionRulePublicationAuditService(operationAuditApi);

        service.recordRejected(7L, 9L, "corr-1", "INSPECTION_RULE_DISABLE", "20",
                Map.of("revisionId", 20L, "errorCode", "100"));

        verify(operationAuditApi).record(7L, 9L, "corr-1", "INSPECTION_RULE_DISABLE",
                "InspectionRuleRevision", "20", "REJECTED",
                Map.of("revisionId", 20L, "errorCode", "100"));
        Method method = InspectionRulePublicationAuditService.class.getMethod("recordRejected",
                Long.class, Long.class, String.class, String.class, String.class, Map.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }
}
