package cn.iocoder.yudao.module.pms.project.controller.admin.servicelevel.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 客户服务等级分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerServiceLevelPageReqVO extends PageParam {

    @Schema(description = "客户编号", example = "1")
    private Long customerId;

    @Schema(description = "服务等级 STRATEGIC/IMPORTANT/STANDARD/GENERAL", example = "STRATEGIC")
    private String level;

    @Schema(description = "状态：0草稿 1已生效 2已停用 3已归档", example = "0")
    private Integer status;

}
