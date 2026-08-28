package cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 客户主档分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerPageReqVO extends PageParam {

    private String code;
    private String name;
    private String departmentCode;
    private String marketCode;
    private String systemCode;
    private String expendCode;
    private String industryCode;
    private String lifecycleStatus;
    private String sourceType;
}
