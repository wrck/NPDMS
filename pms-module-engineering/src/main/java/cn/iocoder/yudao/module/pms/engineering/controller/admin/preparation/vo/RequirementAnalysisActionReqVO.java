package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class RequirementAnalysisActionReqVO {
    @NotNull @PositiveOrZero private Integer expectedContentVersion;
    private Integer expectedProjectVersion;
    private cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection execution;
}
