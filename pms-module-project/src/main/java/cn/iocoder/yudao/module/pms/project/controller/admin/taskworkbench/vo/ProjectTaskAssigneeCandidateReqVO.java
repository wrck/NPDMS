package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目任务负责人候选分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskAssigneeCandidateReqVO extends PageParam {

    @Schema(description = "用户名、昵称或工号关键字")
    @Size(max = 64, message = "候选关键字长度不能超过64个字符")
    private String keyword;

    @AssertTrue(message = "候选分页大小不能超过100")
    @Schema(hidden = true)
    public boolean isCandidatePageSizeValid() {
        return getPageSize() != null && getPageSize() <= 100;
    }
}
