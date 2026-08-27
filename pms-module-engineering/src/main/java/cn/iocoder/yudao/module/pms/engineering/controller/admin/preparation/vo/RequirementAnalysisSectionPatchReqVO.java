package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class RequirementAnalysisSectionPatchReqVO {
    @NotEmpty private Set<String> submittedFields;
    private JsonNode value;
    @Valid private List<RequirementAnalysisAttachmentReqVO> attachments;
    @NotNull @PositiveOrZero private Integer expectedPreparationVersion;
    @NotNull @PositiveOrZero private Integer expectedContentVersion;
    private Integer expectedProjectVersion;
}
