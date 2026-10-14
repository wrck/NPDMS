package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 割接首页两类列表的独立分页与筛选。身份及项目范围由服务端提供。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverWorkbenchTaskPageReqVO extends PageParam {
    @NotBlank
    @Pattern(regexp = "TODO|SELF_CREATED")
    private String listKind;
    @Positive
    private Long projectId;
    @Pattern(regexp = "P2|P3|P4|P5|P6")
    private String currentStage;
    @Pattern(regexp = "GRADE_CONFIRMING|SURVEYING|PLAN_DRAFTING|APPROVING|CLOSURE_IN_PROGRESS|ARCHIVED|LEGACY_UNKNOWN")
    private String taskStatus;
}
