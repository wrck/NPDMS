package cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProjectSplitItemMapper extends BaseMapperX<ProjectSplitItemDO> {
    default List<ProjectSplitItemDO> selectByRequestId(Long requestId) {
        return selectList(new LambdaQueryWrapperX<ProjectSplitItemDO>()
                .eq(ProjectSplitItemDO::getSplitRequestId, requestId)
                .orderByAsc(ProjectSplitItemDO::getTreeSort, ProjectSplitItemDO::getId));
    }
    @Delete("DELETE FROM proj_project_split_item WHERE tenant_id = #{tenantId} AND split_request_id = #{requestId}")
    int physicallyDeleteByRequestId(@Param("tenantId") Long tenantId, @Param("requestId") Long requestId);
}
