package cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SitePageReqVO extends PageParam {

    private String code;
    private String name;
    private Long customerId;
    private Integer status;

}
