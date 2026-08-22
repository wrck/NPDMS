package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "项目实施站点引用")
public class ProjectSiteReqVO {
    @NotNull(message = "站点ID不能为空")
    private Long siteId;
    @NotNull(message = "站点版本不能为空")
    private Integer siteVersion;
    private Boolean primarySite;
}
