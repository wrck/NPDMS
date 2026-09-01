package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectStageGateProcessStartReqVO {
    @Size(max = 128)
    private String processDefinitionId;
}
