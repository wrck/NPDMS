package cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - PMS 客户联系人分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerContactPageReqVO extends PageParam {

    @Schema(description = "客户编号", example = "2048")
    private Long customerId;

    @Schema(description = "姓名，模糊匹配", example = "张三")
    private String name;

    @Schema(description = "是否主联系人", example = "true")
    private Boolean primaryFlag;

    @Schema(description = "状态：0启用，1停用", example = "0")
    private Integer status;

}
