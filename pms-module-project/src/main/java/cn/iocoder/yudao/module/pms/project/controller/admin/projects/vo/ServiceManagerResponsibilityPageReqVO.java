package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 服务经理责任分布分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceManagerResponsibilityPageReqVO extends PageParam {

    @Schema(description = "可选的实际项目节点ID")
    private Long projectId;

    @AssertTrue(message = "责任分布分页大小不能超过100")
    @Schema(hidden = true)
    public boolean isResponsibilityPageSizeValid() {
        return getPageSize() != null && getPageSize() <= 100;
    }
}
