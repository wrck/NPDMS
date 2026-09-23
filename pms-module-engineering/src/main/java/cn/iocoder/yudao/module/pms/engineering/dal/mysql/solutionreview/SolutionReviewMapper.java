package cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SolutionReviewMapper extends BaseMapperX<SolutionReviewDO> {
    cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO source(@Param("query") SolutionReviewQuery query);
    SolutionReviewDO bySolution(@Param("query") SolutionReviewQuery query);
    SolutionReviewDO byInstance(@Param("tenantId") Long tenantId, @Param("instanceId") String instanceId);
    record SolutionReviewQuery(Long tenantId, Long projectId, Long solutionId, boolean lock) { }
}
