package cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectSplitPreviewRespVO {
    private Long requestId;
    private Integer draftVersion;
    private Boolean valid;
    private String previewHash;
    private LocalDateTime validatedAt;
    private Integer parentVersion;
    private Long scopeVersion;
    private Long treeVersion;
    private List<String> errors;
    private List<Item> items;

    @Data
    public static class Item {
        private String clientItemKey;
        private Boolean valid;
        private List<String> errors;
    }
}
