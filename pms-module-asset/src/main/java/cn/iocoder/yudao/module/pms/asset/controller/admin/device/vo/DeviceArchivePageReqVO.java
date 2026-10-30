package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备档案分页查询 Request VO（ast_device 承载，自 pms_equipment_retired 旧链分页承接）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理后台 - 设备档案分页查询 Request VO")
public class DeviceArchivePageReqVO extends PageParam {

    @Schema(description = "业务选择项目：当前项目设备或其有效关联合同的设备")
    @jakarta.validation.constraints.Positive
    private Long selectionProjectId;

    @Schema(description = "业务选择器序列号或当前产品型号，包含匹配；仅selectionProjectId场景")
    private String keyword;

    @jakarta.validation.constraints.AssertTrue(message = "keyword仅可用于项目设备选择")
    public boolean isKeywordSelectionScoped() {
        return keyword == null || keyword.isBlank() || selectionProjectId != null;
    }

    @Schema(description = "产品型号，模糊匹配")
    private String productModel;

    @Schema(description = "合同号，模糊匹配")
    private String contractNo;

    @Schema(description = "设备序列号（档案精确匹配；业务选择器模糊匹配）", example = "SN2026")
    private String sn;

    @Schema(description = "设备名称，模糊匹配", example = "网关")
    private String name;

    @Schema(description = "设备状态", example = "IN_STOCK")
    private String status;

    @Schema(description = "所属项目编号", example = "1")
    private Long projectId;

    @Schema(description = "所属客户编号", example = "1")
    private Long customerId;
}
