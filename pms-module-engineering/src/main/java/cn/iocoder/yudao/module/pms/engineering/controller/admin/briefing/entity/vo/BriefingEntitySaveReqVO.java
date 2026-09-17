package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 复制旧交底保存契约；来源、审核和生命周期字段不接受客户端写入。 */
@Data
@Schema(description = "工程交底独立实体新增/修改请求")
public class BriefingEntitySaveReqVO {
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "交底书编号；租户内唯一，创建后不可变")
    @NotBlank
    @Size(max = 64)
    private String code;

    @Schema(description = "关联项目 ID")
    @NotNull
    private Long projectId;

    @Schema(description = "交底书名称")
    @NotBlank
    @Size(max = 200)
    private String name;

    @Schema(description = "STANDARD 标准 / EMERGENCY 紧急 / CUSTOM 自定义")
    @Size(max = 32)
    private String briefingType;

    @Schema(description = "关联模板 ID")
    private Long templateId;

    @Schema(description = "固定的模板快照 JSON")
    private String templateSnapshot;

    @Schema(description = "前序基线数据快照 JSON")
    private String sourceSnapshot;

    @Schema(description = "交底内容富文本")
    private String content;

    @Schema(description = "文件 URL")
    @Size(max = 512)
    private String fileUrl;

    @Schema(description = "文件名")
    @Size(max = 200)
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件校验值")
    @Size(max = 64)
    private String fileChecksum;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "编制人")
    private Long creatorUserId;

    @Schema(description = "备注")
    @Size(max = 500)
    private String remark;

}
