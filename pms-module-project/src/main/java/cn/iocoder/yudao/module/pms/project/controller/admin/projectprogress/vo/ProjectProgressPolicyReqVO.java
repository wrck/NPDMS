package cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProjectProgressPolicyReqVO {
    @NotBlank
    private String policyType;
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long childProjectId;
        @NotNull
        @DecimalMin("0.0000")
        @DecimalMax("100.0000")
        private BigDecimal weight;
        private List<@NotBlank String> includeStatuses;
    }
}
