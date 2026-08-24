package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 项目业务属性人工调整 Response VO")
@Data
public class ProjectAttributeClassifyRespVO {
    private Long projectId;
    private Integer version;
    private String matchResult;
    private String impactResult;
    private String operationId;
}
