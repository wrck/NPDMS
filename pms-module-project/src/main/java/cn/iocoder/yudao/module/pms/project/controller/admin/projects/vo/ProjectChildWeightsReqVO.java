package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 直接子项目权重整组更新 Request VO")
@Data
public class ProjectChildWeightsReqVO {

    @NotEmpty(message = "直接子项目权重不能为空")
    @Valid
    private List<Item> children;

    @Data
    public static class Item {

        @NotNull(message = "子项目编号不能为空")
        private Long projectId;

        @NotNull(message = "子项目权重不能为空")
        @DecimalMin(value = "0.00", message = "子项目权重不能小于0")
        @DecimalMax(value = "100.00", message = "子项目权重不能大于100")
        @Digits(integer = 3, fraction = 2, message = "子项目权重最多保留两位小数")
        private BigDecimal weight;
    }
}
