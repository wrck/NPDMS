package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;

import java.util.List;

/** 修订014：外部参考事实不能成为独立割接的通用准入条件。 */
final class CutoverRuntimeContextSupport {

    private CutoverRuntimeContextSupport() {
    }

    static boolean requiresImplementationReadiness(String sourceType) {
        return "PROJECT_EVENT".equals(sourceType);
    }

    static CutoverReadinessPort.ReadinessFact independentReadiness(Long projectId, List<Long> deviceIds) {
        // 明确记录不适用，不能伪造一个 READY 的实施准备快照。
        return new CutoverReadinessPort.ReadinessFact(null, 0L, "NOT_APPLICABLE", projectId,
                deviceIds, null, List.of());
    }

    static CutoverCustomerLevelPort.CustomerLevelFact customerReference(
            CutoverCustomerLevelPort port, CutoverProjectContextPort.ProjectContextFact project) {
        try {
            var fact = project.customerId() == null ? null : port.inspect(project.customerId());
            if (fact != null) return fact;
        } catch (CutoverOwnerFactException exception) {
            if (exception.code() != CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE) throw exception;
        }
        return new CutoverCustomerLevelPort.CustomerLevelFact("UNAVAILABLE", project.customerId(),
                project.customerCode(), project.customerName(), null, null, 0L, null, null);
    }
}
