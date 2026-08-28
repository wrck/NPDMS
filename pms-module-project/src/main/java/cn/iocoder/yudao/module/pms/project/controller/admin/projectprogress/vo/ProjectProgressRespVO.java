package cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProjectProgressRespVO {
    private Long projectId;
    private Long policyRevisionId;
    private Long treeVersion;
    private String sourceWatermark;
    private String status;
    private BigDecimal progress;
    private List<Item> items;

    @Data
    public static class Item {
        private Long childProjectId;
        private Long factVersion;
        private BigDecimal childProgress;
        private BigDecimal normalizedWeight;
        private BigDecimal contribution;
        private String missingReason;
    }
}
