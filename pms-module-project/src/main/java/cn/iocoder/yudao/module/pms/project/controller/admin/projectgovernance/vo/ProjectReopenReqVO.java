package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目受控重开请求")
@Data
public class ProjectReopenReqVO {

    @NotBlank
    @Size(max = 64)
    private String reasonCode;
    @NotBlank
    @Size(max = 1000)
    private String reasonDetail;
    @NotNull
    @Positive
    private Long exceptionCloseSnapshotId;
}
