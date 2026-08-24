package cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProjectSplitDraftSaveReqVO {
    private Integer expectedDraftVersion;
    @NotNull private Long parentProjectId;
    private Long templateRevisionId;
    @Valid @NotEmpty private List<Item> items;

    @Data
    public static class Item {
        @NotBlank @Size(max = 64) private String clientItemKey;
        @NotBlank @Size(max = 255) private String projectName;
        @Size(max = 64) private String businessLevelCode;
        private Integer treeSort;
        @Size(max = 64) private String officeDepartmentCode;
        @Valid @NotEmpty private List<Scope> scopes;
    }

    @Data
    public static class Scope {
        @NotNull private Long orderLineId;
        @NotNull @DecimalMin(value = "0", inclusive = false) private BigDecimal quantity;
        @Size(max = 64) private String officeDepartmentCode;
        private List<@Size(max = 128) String> serialNumbers;
    }
}
