package cn.iocoder.yudao.module.pms.customer.controller.admin.contact;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class ProjectContactPageReqVO extends PageParam {
    @Size(max=64) private String name;
    @Min(0) @Max(1) private Integer status;
}
