package cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;
@Mapper
public interface ProjectSplitScopeMapper extends BaseMapperX<ProjectSplitScopeDO> {
    default List<ProjectSplitScopeDO> selectByItemIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<ProjectSplitScopeDO>()
                .in(ProjectSplitScopeDO::getSplitItemId, itemIds)
                .orderByAsc(ProjectSplitScopeDO::getId));
    }
    @Delete("""
            DELETE scope
            FROM proj_project_split_scope scope
            JOIN proj_project_split_item item ON item.id = scope.split_item_id AND item.tenant_id = scope.tenant_id
            WHERE scope.tenant_id = #{tenantId} AND item.split_request_id = #{requestId}
            """)
    int physicallyDeleteByRequestId(@Param("tenantId") Long tenantId, @Param("requestId") Long requestId);
}
