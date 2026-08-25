package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 项目异常关闭请求")
@Data
public class ProjectExceptionCloseReqVO {

    @NotBlank
    private String guardToken;
    @NotBlank
    @Size(max = 64)
    private String reasonCode;
    @NotBlank
    @Size(max = 1000)
    private String reasonDetail;
    @NotBlank
    @Size(max = 10000)
    private String businessBasis;
    @NotNull
    @Valid
    @Size(max = 200)
    private List<LegacyItem> legacyItems;

    @Data
    public static class LegacyItem {
        @NotBlank
        @Size(max = 64)
        private String type;
        @NotBlank
        @Size(max = 500)
        private String summary;
        @NotBlank
        @Size(max = 128)
        private String owner;
        @NotBlank
        @Size(max = 64)
        private String status;
    }
}
