package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目治理动作分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectGovernancePageReqVO extends PageParam {

    @Schema(description = "项目编号")
    private Long projectId;

    @Schema(description = "治理动作单号")
    private String actionNo;

    @Schema(description = "动作类型 ROLLBACK/DIRECT_CLOSE")
    private String actionType;

    @Schema(description = "状态 0草稿 1已提交 2审批中 3已执行 4已驳回 5已撤回")
    private Integer status;

    @Schema(description = "申请人编号")
    private Long applicantUserId;

}
