package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskPageQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CutoverTaskListMapper extends BaseMapperX<CutoverTaskDO> {
    default PageResult<CutoverTaskDO> selectTaskPage(CutoverTaskPageQuery query) {
        if (query.visibleProjectIds().isEmpty() && query.approvalTaskIds().isEmpty()
                || (query.taskIds() != null && query.taskIds().isEmpty())) {
            return PageResult.empty();
        }
        return selectPage(query.pageParam(), new LambdaQueryWrapperX<CutoverTaskDO>()
                .eq(CutoverTaskDO::getTenantId, query.tenantId())
                .inIfPresent(CutoverTaskDO::getId, query.taskIds())
                .eqIfPresent(CutoverTaskDO::getIntakeSourceType, query.intakeSourceType())
                .eqIfPresent(CutoverTaskDO::getProjectId, query.projectId())
                .eqIfPresent(CutoverTaskDO::getCurrentStage, query.currentStage())
                .eqIfPresent(CutoverTaskDO::getTaskStatus, query.taskStatus())
                .and(scope -> {
                    if (!query.visibleProjectIds().isEmpty()) scope.in(CutoverTaskDO::getProjectId, query.visibleProjectIds());
                    if (!query.approvalTaskIds().isEmpty()) {
                        if (!query.visibleProjectIds().isEmpty()) scope.or();
                        scope.in(CutoverTaskDO::getId, query.approvalTaskIds());
                    }
                })
                .orderByDesc(CutoverTaskDO::getCreateTime)
                .orderByDesc(CutoverTaskDO::getId));
    }
}
