package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 服务经理候选分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceManagerCandidatePageReqVO extends PageParam {

    @Schema(description = "实施站点稳定ID")
    private Long siteId;

    @Schema(description = "确认的办事处部门ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "办事处部门ID不能为空")
    private Long departmentId;

    @Schema(description = "确认的办事处部门编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "办事处部门编码不能为空")
    private String departmentCode;

    @Schema(description = "用户名、昵称或工号关键字")
    @Size(max = 64, message = "候选关键字长度不能超过64个字符")
    private String keyword;

    @AssertTrue(message = "候选分页大小不能超过100")
    @Schema(hidden = true)
    public boolean isCandidatePageSizeValid() {
        return getPageSize() != null && getPageSize() <= 100;
    }
}
