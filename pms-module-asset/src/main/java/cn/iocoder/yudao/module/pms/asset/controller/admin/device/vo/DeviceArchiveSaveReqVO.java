package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备档案创建/更新 Request VO（ast_device 承载）。
 */
@Data
public class DeviceArchiveSaveReqVO {

    /** 更新时必填；创建时忽略 */
    private Long id;

    @NotBlank(message = "设备序列号不能为空")
    @Size(max = 128, message = "设备序列号长度不能超过128")
    private String sn;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称长度不能超过128")
    private String name;

    @Size(max = 128, message = "设备型号长度不能超过128")
    private String productModel;

    private Long customerId;

    private Long projectId;

    private LocalDate warrantyStartDate;

    private LocalDate warrantyEndDate;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;

    @Size(max = 100, message = "补录原因长度不能超过100")
    private String manualReason;

    @Size(max = 300, message = "补录证据长度不能超过300")
    private String manualEvidence;
}
