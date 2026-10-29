package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.system.api.user.ProjectMemberIdentityScopeProvider;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
public class ProjectMemberFilterPageReqVO extends PageParam {
    @NotNull private ProjectMemberIdentityScopeProvider.Filter field;
    @Size(max = 64) private String keyword;
}
