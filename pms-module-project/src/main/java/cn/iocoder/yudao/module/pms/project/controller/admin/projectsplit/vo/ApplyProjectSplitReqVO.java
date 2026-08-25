package cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApplyProjectSplitReqVO {
    @NotNull private Integer expectedParentVersion;
    @NotNull private Long expectedScopeVersion;
    @NotNull private Long expectedTreeVersion;
}
