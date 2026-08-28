package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
public class ProjectWorkspaceRespVO {
    private Long projectId;
    private String projectCode;
    private String projectName;
    private List<String> overviewTabs;
    private List<StageTaskNavigation> stageTaskNavigation;
    private Long taskTreeVersion;
    private String projectionWatermark;
    private Set<String> allowedActions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageTaskNavigation {
        private String stageCode;
        private String stageName;
        private String stageStatus;
        private Long taskCount;
    }
}
