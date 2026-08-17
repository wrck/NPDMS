package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指派一级服务经理 Request VO（旧区间关闭+新区间开启，留痕前后值）
 */
@Schema(description = "管理后台 - 指派一级服务经理 Request VO")
@Data
public class ProjectAssignManagerReqVO {

    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "成员工号", example = "E001")
    private String employeeNo;

    @Schema(description = "成员姓名", example = "张三")
    private String memberName;

    @Schema(description = "生效开始时间（空=当前时间，不得晚于当前时间）")
    private LocalDateTime effectiveFrom;
}
