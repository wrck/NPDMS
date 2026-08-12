package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目模板 Response VO")
@Data
public class ProjectTemplateRespVO {

    @Schema(description = "模板编号")
    private Long id;
    @Schema(description = "模板编码")
    private String code;
    @Schema(description = "模板名称")
    private String name;
    @Schema(description = "适用项目类型")
    private String projectType;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "排序号")
    private Integer sort;
    @Schema(description = "模板内容快照")
    private TemplateSnapshot snapshotJson;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
