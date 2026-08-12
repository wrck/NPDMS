package cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 客户分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerPageReqVO extends PageParam {

    @Schema(description = "客户编码，模糊匹配", example = "CUST")
    private String code;

    @Schema(description = "客户名称，模糊匹配", example = "阿里")
    private String name;

    @Schema(description = "状态：0启用，1停用", example = "0")
    private Integer status;

}
