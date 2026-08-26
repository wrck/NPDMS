package cn.iocoder.yudao.module.pms.project.api.scope;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;

/** 项目范围公开契约；项目树和授权合并算法仅由PROJ实现。 */
public interface ProjectScopeApi {

    String ACTION_VIEW = "PROJECT_VIEW";
    String ACTION_EDIT = "PROJECT_EDIT";
    String ACTION_MANAGE = "PROJECT_MANAGE";

    ProjectScopeResult resolve(ProjectScopeQuery query);

    /** 由PROJ选择当前生效树版本后解析范围。 */
    ProjectScopeResult resolveCurrent(ProjectCurrentScopeQuery query);
}
