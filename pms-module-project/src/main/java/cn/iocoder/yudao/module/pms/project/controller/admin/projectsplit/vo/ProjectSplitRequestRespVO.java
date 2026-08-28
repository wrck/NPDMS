package cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectSplitRequestRespVO {
    private Long id;
    private Long parentProjectId;
    private String status;
    private Integer draftVersion;
    private Integer parentVersion;
    private Long scopeVersion;
    private Long treeVersion;
    private Long templateRevisionId;
    private String previewHash;
    private String validationStatus;
    private LocalDateTime validatedAt;
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private String clientItemKey;
        private String projectName;
        private String businessLevelCode;
        private Integer treeSort;
        private String officeDepartmentCode;
        private String itemStatus;
        private List<Scope> scopes;
    }
    @Data
    public static class Scope {
        private Long id;
        private Long orderLineId;
        private BigDecimal allocatedQty;
        private String officeDepartmentCode;
        private String serialNo;
        private Long sourceScopeVersion;
    }
}
