package cn.iocoder.yudao.module.pms.commerce.api.binding;

import java.math.BigDecimal;
import java.util.List;

/** COM合同订单范围Owner；初始化必须加入PROJ建项事务，订单同步不改写子项目或验收历史。 */
public interface ProjectContractScopeApi {
    void initialize(ScopeContext context);

    record ScopeContext(Long tenantId, Long projectId, Long subjectUserId, Long expectedProjectVersion,
                        Long expectedTreeVersion, Long expectedScopeVersion, String operationId) { }

    record ScopeLineChange(Long orderLineId, String orderNo, String lineNo, String productCode,
                           String productDesc, String sourceVersion, BigDecimal orderQuantity,
                           BigDecimal allocatedElsewhere, BigDecimal currentQuantity,
                           BigDecimal proposedQuantity, String changeType, List<String> errors) { }

    record ScopePreview(Long projectId, Long scopeVersion, String sourceDigest,
                        List<ScopeLineChange> lines, List<String> errors, int changedLineCount, boolean valid) { }

    record ScopeSynchronizationResult(Long projectId, Long scopeVersion, int changedLineCount) { }
}
