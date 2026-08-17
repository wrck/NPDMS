package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台 - 项目模板四维匹配预演 Response VO（F-PM03 BR-4）
 * <p>
 * 唯一命中返回模板；无匹配/同优先级多匹配返回冲突清单，不静默选模。
 */
@Schema(description = "管理后台 - 项目模板四维匹配预演 Response VO")
@Data
public class ProjectTemplateMatchRespVO {

    @Schema(description = "匹配结局：MATCHED唯一命中/NO_MATCH无匹配/MULTI_MATCH同优先级多匹配", example = "MATCHED")
    private String outcome;

    @Schema(description = "唯一命中时的候选模板（其余结局为 null）")
    private TemplateMatchCandidate matched;

    @Schema(description = "冲突/未命中说明清单（人工处理）")
    private List<String> conflicts = new ArrayList<>();
}
