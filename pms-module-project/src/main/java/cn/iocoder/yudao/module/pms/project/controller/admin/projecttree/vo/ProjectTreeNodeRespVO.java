package cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 项目树节点 Response VO")
@Data
public class ProjectTreeNodeRespVO {

    @Schema(description = "项目编号", example = "1024")
    private Long id;

    @Schema(description = "项目编码", example = "PMS202401001")
    private String code;

    @Schema(description = "项目名称", example = "项目A")
    private String name;

    @Schema(description = "父项目编号", example = "1")
    private Long parentId;

    @Schema(description = "根项目编号", example = "1")
    private Long rootId;

    @Schema(description = "物化路径", example = "/1/1024/")
    private String path;

    @Schema(description = "路径深度", example = "0")
    private Integer depth;

    @Schema(description = "同级排序号", example = "0")
    private Integer sort;

    @Schema(description = "项目分类", example = "战略")
    private String category;

    @Schema(description = "是否重大项目", example = "false")
    private Boolean majorProjectFlag;

    @Schema(description = "项目经理编号", example = "1")
    private Long managerUserId;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "子节点列表")
    private List<ProjectTreeNodeRespVO> children;

}
