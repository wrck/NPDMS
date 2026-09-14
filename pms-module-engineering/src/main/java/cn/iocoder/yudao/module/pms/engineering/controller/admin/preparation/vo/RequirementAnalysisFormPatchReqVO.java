package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class RequirementAnalysisFormPatchReqVO {
    @NotNull
    private Map<String, Object> values;
    private cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection execution;
}
