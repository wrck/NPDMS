package cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 业务联调新增/修改 Request VO（FR-ENG-024）。
 */
@Schema(description = "管理后台 - 业务联调新增/修改 Request VO")
@Data
public class JointTestSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "联调编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "JT-2026-001")
    @NotBlank(message = "联调编码不能为空")
    private String code;

    @Schema(description = "联调用例", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机连通性测试")
    @NotBlank(message = "联调用例不能为空")
    private String testCase;

    @Schema(description = "关联设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "参与方", example = "甲方、乙方")
    private String participants;

    @Schema(description = "联调时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime testTime;

    @Schema(description = "联调人", example = "1")
    private Long testerUserId;

    @Schema(description = "联调结果")
    private String result;

    @Schema(description = "异常记录")
    private String exceptionRecord;

    @Schema(description = "证据附件")
    private String evidenceUrl;

    @Schema(description = "状态：0 待联调 1 进行中 2 通过 3 失败", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
