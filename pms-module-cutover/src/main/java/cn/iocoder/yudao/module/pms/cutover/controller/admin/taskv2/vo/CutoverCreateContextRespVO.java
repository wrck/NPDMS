package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;

import java.util.List;

/** 创建上下文HTTP投影；将PROJ department事实明确映射为办事处展示字段。 */
public record CutoverCreateContextRespVO(List<Candidate> candidates, boolean selectionRequired,
                                         List<CutoverTaskViews.ConfigurationChoice> configurationChoices,
                                         boolean configurationSelectionRequired) {

    public record Project(Long projectId, int projectVersion, String projectCode, String projectName,
                          Long customerId, String customerCode, String customerName,
                          Long officeDepartmentId, String officeCode, String officeName,
                          long projectScopeVersion) {
    }

    public record Candidate(Project project, List<CutoverDeviceScopePort.DeviceFact> devices,
                            CutoverCustomerLevelPort.CustomerLevelFact customerServiceLevel,
                            CutoverReadinessPort.ReadinessFact implementationReadiness,
                            boolean createAllowed) {
    }
}
