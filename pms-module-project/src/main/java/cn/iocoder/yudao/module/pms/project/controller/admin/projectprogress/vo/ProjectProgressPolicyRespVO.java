package cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectProgressPolicyRespVO {
    private Long id;
    private Long parentProjectId;
    private Integer revisionNo;
    private String status;
    private String policyType;
    private String processInstanceId;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Integer version;
    private List<Item> items;

    @Data
    public static class Item {
        private Long childProjectId;
        private BigDecimal weight;
        private List<String> includeStatuses;
    }
}
