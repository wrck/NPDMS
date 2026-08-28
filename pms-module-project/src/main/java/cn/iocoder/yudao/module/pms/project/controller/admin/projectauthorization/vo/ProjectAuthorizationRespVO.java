package cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目授权 Response VO")
@Data
public class ProjectAuthorizationRespVO {

    private Long id;
    private Long subjectUserId;
    private Long projectId;
    private String actionCode;
    private String scopeCode;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String statusCode;
    private Long grantedBy;
    private LocalDateTime grantedAt;
    private Long revokedBy;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private Integer version;
}
