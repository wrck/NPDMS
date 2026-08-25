package cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AreaDepartmentMappingPageReqVO extends PageParam {

    private String areaCode;
    private String areaLevel;
    private String departmentCode;
    private Integer status;

}
