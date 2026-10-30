package cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewPolicyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SolutionReviewPolicyMapper extends BaseMapperX<SolutionReviewPolicyDO> {
    record Selection(Long tenantId, Long projectId, Long solutionId) { }
    default SolutionReviewPolicyDO bySolution(Selection query) {
        return selectOne(new LambdaQueryWrapperX<SolutionReviewPolicyDO>()
                .eq(SolutionReviewPolicyDO::getTenantId, query.tenantId())
                .eq(SolutionReviewPolicyDO::getProjectId, query.projectId())
                .eq(SolutionReviewPolicyDO::getSolutionId, query.solutionId()));
    }
}
