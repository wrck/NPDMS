package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 配置调试分页 Request VO（FR-ENG-023）。
 */
@Schema(description = "管理后台 - 配置调试分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigurationPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "配置编码", example = "CFG-2026-001")
    private String code;

    @Schema(description = "状态：0 待调试 1 进行中 2 已完成 3 异常", example = "0")
    private Integer status;

    @Schema(description = "设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "调试人", example = "1")
    private Long debuggerUserId;
}
