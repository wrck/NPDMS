package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;

import java.util.List;
import java.util.Objects;

/** 修订014：外部参考事实不能成为独立割接的通用准入条件。 */
final class CutoverContextSupport {

    private CutoverContextSupport() {
    }

    static boolean requiresImplementationReadiness(String sourceType) {
        return "PROJECT_EVENT".equals(sourceType);
    }

    /** P1原有冻结契约；重大项目级别由P3匹配单独按项目版本重验。 */
    static boolean sameFrozenProject(CutoverProjectContextPort.ProjectContextFact expected,
                                     CutoverProjectContextPort.ProjectContextFact current) {
        return expected != null && current != null
                && Objects.equals(expected.tenantId(), current.tenantId())
                && Objects.equals(expected.projectId(), current.projectId())
                && expected.projectVersion() == current.projectVersion()
                && Objects.equals(expected.projectCode(), current.projectCode())
                && Objects.equals(expected.projectName(), current.projectName())
                && Objects.equals(expected.customerId(), current.customerId())
                && Objects.equals(expected.customerCode(), current.customerCode())
                && Objects.equals(expected.customerName(), current.customerName())
                && Objects.equals(expected.departmentId(), current.departmentId())
                && Objects.equals(expected.departmentCode(), current.departmentCode())
                && Objects.equals(expected.departmentName(), current.departmentName())
                && expected.projectScopeVersion() == current.projectScopeVersion();
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
