package cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 客户 Response VO")
@Data
public class CustomerRespVO {

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "客户编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "CUST001")
    private String code;

    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "阿里巴巴")
    private String name;

    @Schema(description = "客户简称", example = "阿里")
    private String shortName;

    @Schema(description = "状态：0启用，1停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "地址", example = "杭州市余杭区")
    private String address;

    @Schema(description = "备注", example = "重要客户")
    private String remark;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    private Boolean legacyReadOnly;

    private String replacementPath;

}
