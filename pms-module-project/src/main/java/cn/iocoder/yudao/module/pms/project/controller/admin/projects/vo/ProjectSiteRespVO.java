package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "项目实施站点关系")
public class ProjectSiteRespVO {
    private Long id;
    private Long projectId;
    private Long siteId;
    private Integer siteVersionSnapshot;
    private Boolean primarySite;
    private String scopeStatus;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String siteCodeSnapshot;
    private String siteNameSnapshot;
    private String addressSnapshot;
    private Integer version;
}
