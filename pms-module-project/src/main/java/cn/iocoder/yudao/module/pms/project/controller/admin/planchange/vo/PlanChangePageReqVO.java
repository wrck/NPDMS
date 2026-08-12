package cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 计划变更分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanChangePageReqVO extends PageParam {

    @Schema(description = "项目编号")
    private Long projectId;

    @Schema(description = "变更单号")
    private String changeNo;

    @Schema(description = "变更标题")
    private String title;

    @Schema(description = "变更类型 PLAN_ADJUST/SCOPE_CHANGE/DATE_SHIFT/OTHER")
    private String changeType;

    @Schema(description = "状态 0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止")
    private Integer status;

    @Schema(description = "申请人编号")
    private Long applicantUserId;

}
