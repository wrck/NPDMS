package cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface SolutionCompletionMapper extends cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX<SolutionDO> {
    SolutionDO one(@Param("query") SolutionCompletionQuery query);
    default List<SolutionDO> page(SolutionCompletionQuery query) {
        return selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SolutionDO>(1, query.pageSize(), false),
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<SolutionDO>()
                        .eq(SolutionDO::getTenantId, query.tenantId()).eq(SolutionDO::getProjectId, query.projectId())
                        .gtIfPresent(SolutionDO::getId, query.afterId())
                        .eq(SolutionDO::getSolutionType, "IMPLEMENTATION").notIn(SolutionDO::getStatus, 5, 6)
                        .orderByAsc(SolutionDO::getId)).getRecords();
    }
}
