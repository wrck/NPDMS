package cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 业务联调分页 Request VO（FR-ENG-024）。
 */
@Schema(description = "管理后台 - 业务联调分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JointTestPageReqVO extends PageParam {

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "联调编码", example = "JT-2026-001")
    private String code;

    @Schema(description = "联调用例", example = "连通性")
    private String testCase;

    @Schema(description = "状态：0 待联调 1 进行中 2 通过 3 失败", example = "0")
    private Integer status;

    @Schema(description = "关联设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "联调人", example = "1")
    private Long testerUserId;
}
