package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 设备分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class DevicePageReqVO extends PageParam {

    private String sn;
    private String productCode;
    private Long projectId;
    private Long customerId;
}
