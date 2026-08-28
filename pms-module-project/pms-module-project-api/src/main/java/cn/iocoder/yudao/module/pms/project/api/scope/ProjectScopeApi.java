package cn.iocoder.yudao.module.pms.project.api.scope;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;

import java.util.Set;

/** 项目范围公开契约；项目树和授权合并算法仅由PROJ实现。 */
public interface ProjectScopeApi {

    String ACTION_VIEW = "PROJECT_VIEW";
    String ACTION_EDIT = "PROJECT_EDIT";
    String ACTION_MANAGE = "PROJECT_MANAGE";

    ProjectScopeResult resolve(ProjectScopeQuery query);

    /** 由PROJ选择当前生效树版本后解析范围。 */
    ProjectScopeResult resolveCurrent(ProjectCurrentScopeQuery query);

    /** 用于分页入口和跨模块数据范围裁剪，不返回路径占位项目。 */
    Set<Long> resolveAllCurrent(ProjectAllScopeQuery query);

    /** 锁住当前根树版本至外层事务结束；调用方必须比较返回版本与expectedScopeVersion。 */
    ProjectScopeResult lockAndRevalidate(ProjectScopeRevalidationQuery query);
}
