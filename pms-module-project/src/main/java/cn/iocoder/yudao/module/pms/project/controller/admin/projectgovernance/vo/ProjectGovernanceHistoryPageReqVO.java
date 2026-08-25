package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目治理历史分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectGovernanceHistoryPageReqVO extends PageParam {
}
