package cn.iocoder.yudao.module.pms.service.service.inspectionrule.audit;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class InspectionRulePublicationAuditService {

    private static final String AGGREGATE_TYPE = "InspectionRuleRevision";

    private final OperationAuditApi operationAuditApi;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordRejected(Long tenantId, Long actorId, String correlationId, String operationCode,
                               String aggregateKey, Map<String, ?> safeDetail) {
        operationAuditApi.record(tenantId, actorId, correlationId, operationCode,
                AGGREGATE_TYPE, aggregateKey, "REJECTED", safeDetail);
    }
}
