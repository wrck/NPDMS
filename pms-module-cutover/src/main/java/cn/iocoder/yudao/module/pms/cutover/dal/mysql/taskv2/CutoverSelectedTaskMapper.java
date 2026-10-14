package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.*;

@Mapper
public interface CutoverSelectedTaskMapper extends BaseMapperX<CutoverTaskDO> {
    default PageResult<CutoverTaskDO> selectedPage(SelectedTaskPageQuery query) {
        if (query.projectIds().isEmpty() && query.approvalTaskIds().isEmpty() || query.taskIds().isEmpty()) return new PageResult<>(List.of(), 0L);
        var page = new PageParam(); page.setPageNo(query.pageNo()); page.setPageSize(query.pageSize());
        return selectPage(page, new LambdaQueryWrapperX<CutoverTaskDO>()
                .eq(CutoverTaskDO::getTenantId, query.tenantId()).in(CutoverTaskDO::getId, query.taskIds())
                .and(scope -> {
                    if (!query.projectIds().isEmpty()) scope.in(CutoverTaskDO::getProjectId, query.projectIds());
                    if (!query.approvalTaskIds().isEmpty()) {
                        if (!query.projectIds().isEmpty()) scope.or();
                        scope.in(CutoverTaskDO::getId, query.approvalTaskIds());
                    }
                }).orderByDesc(CutoverTaskDO::getCreateTime).orderByDesc(CutoverTaskDO::getId));
    }
    record SelectedTaskPageQuery(Long tenantId, Set<Long> projectIds, Set<Long> taskIds, Set<Long> approvalTaskIds, int pageNo, int pageSize) {
        public SelectedTaskPageQuery { projectIds = Set.copyOf(projectIds); taskIds = Set.copyOf(taskIds); approvalTaskIds = Set.copyOf(approvalTaskIds); }
    }
}
