package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备档案状态变更 Request VO（状态机校验 + 版本历史追加）。
 */
@Data
public class DeviceArchiveStatusChangeReqVO {

    @NotBlank(message = "状态机动作不能为空")
    private String action;

    /** 仅 completeRepair 需要：目标状态 IN_STOCK/IN_USE */
    private String targetStatus;

    @Size(max = 500, message = "变更说明长度不能超过500")
    private String changeDescription;
}
