package cn.iocoder.yudao.module.system.controller.admin.company.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyPageReqVO extends PageParam {

    private String code;
    private String name;
    private Integer status;

}
