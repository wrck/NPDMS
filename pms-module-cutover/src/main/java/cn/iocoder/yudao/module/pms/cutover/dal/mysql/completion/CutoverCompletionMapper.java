package cn.iocoder.yudao.module.pms.cutover.dal.mysql.completion;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface CutoverCompletionMapper extends cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX<CutoverTaskDO> {
    CutoverTaskDO one(@Param("query") CutoverCompletionQuery query);
    default List<CutoverTaskDO> page(CutoverCompletionQuery query) {
        return selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<CutoverTaskDO>(1, query.pageSize(), false),
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CutoverTaskDO>()
                        .eq(CutoverTaskDO::getTenantId, query.tenantId()).eq(CutoverTaskDO::getProjectId, query.projectId())
                        
                        .gtIfPresent(CutoverTaskDO::getId, query.afterId()).orderByAsc(CutoverTaskDO::getId)).getRecords();
    }
}
