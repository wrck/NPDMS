package cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台 - 现场培训记录 Response VO（ACC-01）。
 */
@Schema(description = "管理后台 - 现场培训记录 Response VO")
@Data
public class TrainingRespVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "培训记录编码", example = "TR-2026-001")
    private String code;

    @Schema(description = "培训名称", example = "设备运维培训")
    private String name;

    @Schema(description = "客户联系人")
    private String contactName;

    @Schema(description = "客户联系电话")
    private String contactPhone;

    @Schema(description = "培训类型，逗号分隔")
    private String trainingTypes;

    @Schema(description = "培训时间")
    private LocalDate trainingTime;

    @Schema(description = "培训工程师用户编号")
    private Long trainerUserId;

    @Schema(description = "培训工程师姓名快照")
    private String trainerName;

    @Schema(description = "参训人数")
    private Integer traineeCount;

    @Schema(description = "培训内容")
    private String content;

    @Schema(description = "状态：0草稿 1已外发 2客户已确认 3已作废")
    private Integer status;

    @Schema(description = "外发令牌有效期")
    private LocalDateTime tokenExpiresAt;

    @Schema(description = "客户评价：培训工程师技术水平及表达能力")
    private String skillRating;

    @Schema(description = "客户评价：培训内容及讲解效果")
    private String effectRating;

    @Schema(description = "客户评价：培训满意度")
    private String satisfactionRating;

    @Schema(description = "客户综合意见")
    private String signOpinion;

    @Schema(description = "客户签字人")
    private String signConfirmerName;

    @Schema(description = "客户确认时间")
    private LocalDateTime signTime;

    @Schema(description = "培训记录表文件URL")
    private String fileUrl;

    @Schema(description = "培训记录表文件名")
    private String fileName;

    @Schema(description = "培训记录表文件大小")
    private Long fileSize;

    @Schema(description = "培训记录表文件SHA-256校验值")
    private String fileChecksum;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
