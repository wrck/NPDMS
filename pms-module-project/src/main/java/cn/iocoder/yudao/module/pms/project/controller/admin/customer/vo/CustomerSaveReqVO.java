package cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - PMS 客户创建/修改 Request VO")
@Data
public class CustomerSaveReqVO {

    @Schema(description = "客户编号", example = "1024")
    private Long id;

    @Schema(description = "客户编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CUST001")
    @NotBlank(message = "客户编码不能为空")
    @Size(max = 64, message = "客户编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿里巴巴")
    @NotBlank(message = "客户名称不能为空")
    @Size(max = 128, message = "客户名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "客户简称", example = "阿里")
    @Size(max = 64, message = "客户简称长度不能超过 64 个字符")
    private String shortName;

    @Schema(description = "状态：0启用，1停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "地址", example = "杭州市余杭区")
    @Size(max = 255, message = "地址长度不能超过 255 个字符")
    private String address;

    @Schema(description = "备注", example = "重要客户")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

}
