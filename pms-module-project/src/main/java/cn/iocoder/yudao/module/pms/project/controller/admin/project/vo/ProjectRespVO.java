package cn.iocoder.yudao.module.pms.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 项目 Response VO")
@Data
public class ProjectRespVO {

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "项目编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PMS202401001")
    private String code;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "项目A")
    private String name;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long customerId;

    @Schema(description = "合同编码", example = "HT202401001")
    private String contractCode;

    @Schema(description = "所属办公室编号", example = "1")
    private Long officeId;

    @Schema(description = "销售人员编号", example = "1")
    private Long salesUserId;

    @Schema(description = "行业", example = "制造业")
    private String industry;

    @Schema(description = "实施方式", example = "自营")
    private String implementationMode;

    @Schema(description = "项目类型", example = "实施")
    private String projectType;

    @Schema(description = "出货状态", example = "未出货")
    private String shipmentStatus;

    @Schema(description = "来源系统", requiredMode = Schema.RequiredMode.REQUIRED, example = "ERP")
    private String sourceSystem;

    @Schema(description = "来源业务键", requiredMode = Schema.RequiredMode.REQUIRED, example = "ERP-001")
    private String sourceBusinessKey;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

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

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
