package cn.iocoder.yudao.module.pms.asset.controller.admin.producttype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 设备当前产品类型输入")
@Data
public class DeviceCurrentProductTypeReqVO {

    @NotNull(message = "设备ID不能为空")
    private Long deviceId;

    @NotBlank(message = "解析状态不能为空")
    private String resolutionStatus;
}
