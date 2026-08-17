package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板匹配 Response VO（创建向导第②步：命中生效模板列表，含版本概要）
 */
@Schema(description = "管理后台 - 项目创建模板匹配 Response VO")
@Data
public class ProjectMatchTemplatesRespVO {

    @Schema(description = "匹配结局：MATCHED唯一命中/NO_MATCH无匹配/MULTI_MATCH同优先级多匹配")
    private String outcome;

    @Schema(description = "命中候选清单（MATCHED=单元素，MULTI_MATCH=同优先级候选，NO_MATCH=空）")
    private List<CandidateItem> candidates = new ArrayList<>();

    @Schema(description = "冲突/未命中说明清单（人工处理提示）")
    private List<String> conflicts = new ArrayList<>();

    @Data
    public static class CandidateItem {
        @Schema(description = "模板ID")
        private Long templateId;
        @Schema(description = "模板编码")
        private String code;
        @Schema(description = "模板名称")
        private String name;
        @Schema(description = "匹配优先级（数值小者先命中）")
        private Integer matchPriority;
        @Schema(description = "最新已发布版本号")
        private Integer latestRevisionNo;
        @Schema(description = "匹配条件：签约方式（null=不限）")
        private String signingMethod;
        @Schema(description = "匹配条件：项目类别（null=不限）")
        private String projectCategory;
        @Schema(description = "匹配条件：实施方式（null=不限）")
        private String implementationMethod;
        @Schema(description = "匹配条件：重大项目级别（null=不限）")
        private String majorProjectLevel;
    }
}
