package cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台 - 现场培训记录新增/修改 Request VO（ACC-01）。
 */
@Schema(description = "管理后台 - 现场培训记录新增/修改 Request VO")
@Data
public class TrainingSaveReqVO {

    @Schema(description = "编号（修改时必填）", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "2048")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "培训名称", example = "设备运维培训")
    @NotBlank(message = "培训名称不能为空")
    @Size(max = 255, message = "培训名称不能超过 255 个字符")
    private String name;

    @Schema(description = "客户联系人（自动带入用户联系人）", example = "张三")
    private String contactName;

    @Schema(description = "客户联系电话", example = "13800138000")
    private String contactPhone;

    @Schema(description = "培训类型集合", example = "[\"TECHNICAL_PRINCIPLE\",\"PRODUCT_OPS\"]")
    @NotEmpty(message = "培训类型不能为空")
    private List<String> trainingTypes;

    @Schema(description = "培训时间", example = "2026-09-19")
    @NotNull(message = "培训时间不能为空")
    private LocalDate trainingTime;

    @Schema(description = "培训工程师用户编号，留空取当前登录人", example = "1")
    private Long trainerUserId;

    @Schema(description = "参训人数", example = "12")
    private Integer traineeCount;

    @Schema(description = "培训内容")
    private String content;

    @Schema(description = "备注")
    private String remark;
}
