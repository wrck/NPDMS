package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectStageGateProcessStartReqVO {
    /** Optional assertion of the frozen ID. Omission uses the effective plan; another definition is rejected. */
    @Size(max = 128)
    private String processDefinitionId;
}
