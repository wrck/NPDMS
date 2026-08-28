package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 任务状态机草稿 Request VO")
@Data
public class TaskStateMachineSaveReqVO {

    @NotNull
    private LocalDateTime effectiveFrom;
    @Valid
    @NotEmpty
    private List<Transition> transitions;

    @Data
    public static class Transition {
        @NotBlank @Size(max = 64) private String fromStatusCode;
        @NotBlank @Size(max = 64) private String actionCode;
        @NotBlank @Size(max = 64) private String toStatusCode;
        @NotBlank @Size(max = 64) private String standardStatusMapping;
        @NotBlank @Size(max = 128) private String allowedRoleCode;
        @NotBlank private String entryCondition;
        @NotBlank private String exitCondition;
    }
}
