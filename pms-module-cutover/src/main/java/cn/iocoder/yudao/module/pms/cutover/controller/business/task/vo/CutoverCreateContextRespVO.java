package cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo;

import java.util.List;

/** 创建上下文HTTP投影；将PROJ department事实明确映射为办事处展示字段。 */
public record CutoverCreateContextRespVO(List<Candidate> candidates, boolean selectionRequired,
                                         List<CutoverTaskResponses.ConfigurationChoice> configurationChoices,
                                         boolean configurationSelectionRequired) {

    public record Project(Long projectId, int projectVersion, String projectCode, String projectName,
                          Long customerId, String customerCode, String customerName,
                          Long departmentId, String departmentCode, String departmentName,
                          long projectScopeVersion) {
    }

    public record Candidate(Project project, List<CutoverTaskResponses.Device> devices,
                            CutoverTaskResponses.CustomerLevel customerServiceLevel,
                            CutoverTaskResponses.Readiness implementationReadiness,
                            boolean createAllowed) {
    }
}
