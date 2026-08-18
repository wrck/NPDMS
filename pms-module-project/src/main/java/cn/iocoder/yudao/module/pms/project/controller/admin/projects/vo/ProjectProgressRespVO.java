package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目进度汇总 Response VO（F-PM02 / PM-02）
 */
@Schema(description = "管理后台 - 项目进度汇总 Response VO")
@Data
public class ProjectProgressRespVO {

    @Schema(description = "汇总进度（Σ 直接子项目进度 × 归一化权重，0-100）", example = "75.00")
    private BigDecimal aggregate;

    @Schema(description = "直接子项目进度列表")
    private List<ChildItem> children;

    @Schema(description = "直接子项目进度项")
    @Data
    public static class ChildItem {

        @Schema(description = "子项目ID", example = "2")
        private Long projectId;

        @Schema(description = "子项目编码", example = "PJT2026000001-SP000001")
        private String projectCode;

        @Schema(description = "子项目名称", example = "某客户网络优化工程-子项目1")
        private String projectName;

        @Schema(description = "子项目进度（0-100）", example = "50.00")
        private BigDecimal progress;

        @Schema(description = "归一化权重（0-1，合计=1）", example = "0.5")
        private BigDecimal normalizedWeight;

        @Schema(description = "权重来源（DEFAULT_EQUAL/MANUAL）", example = "DEFAULT_EQUAL")
        private String weightSource;
    }
}
