package cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AddressPageReqVO extends PageParam {

    private String fullAddress;
    private String districtCode;
    private Integer status;

}
