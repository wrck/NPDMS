package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;

import java.util.List;
import java.util.Objects;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;

/** 修订014：外部参考事实不能成为独立割接的通用准入条件。 */
final class CutoverContextSupport {

    private CutoverContextSupport() {
    }

    static boolean requiresImplementationReadiness(String sourceType) {
        return "PROJECT_EVENT".equals(sourceType);
    }

    static boolean sameCustomer(CutoverProjectContextPort.ProjectContextFact left,
                                CutoverProjectContextPort.ProjectContextFact right) {
        return left != null && right != null && Objects.equals(left.customerId(), right.customerId())
                && Objects.equals(left.customerCode(), right.customerCode());
    }

    /** 草稿可更新归属版本，但不能替换原选设备或跨项目续办。事务锁仍核对最新版本。 */
    static boolean sameSelectedDevices(List<CutoverDeviceScopePort.DeviceFact> selected,
                                       List<CutoverDeviceScopePort.DeviceFact> current) {
        return current != null && selected.size() == current.size()
                && current.stream().map(CutoverDeviceScopePort.DeviceFact::deviceId).distinct().count() == selected.size()
                && selected.stream().allMatch(expected -> current.stream().anyMatch(value ->
                Objects.equals(expected.deviceId(), value.deviceId()) && Objects.equals(expected.projectId(), value.projectId())
                        && expected.serialNumber().equalsIgnoreCase(value.serialNumber())));
    }

    /** 仅比较本次创建操作读取与锁定的项目上下文，不绑定后续割接环节。 */
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
            if (fact != null) return fact.toBuilder().customerCode(project.customerCode()).customerName(project.customerName()).build();
        } catch (CutoverOwnerFactException exception) {
            if (exception.code() != CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE) throw exception;
        }
        return CutoverCustomerLevelPort.CustomerLevelFact.builder()
                .status("UNAVAILABLE")
                .customerId(project.customerId())
                .customerCode(project.customerCode())
                .customerName(project.customerName())
                .serviceLevelRevisionId(null)
                .serviceLevelCode(null)
                .factVersion(0L)
                .effectiveFrom(null)
                .effectiveTo(null)
                .build();
    }
}
