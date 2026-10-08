package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.business;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import org.apache.ibatis.annotations.Mapper;
import java.util.Set;
@Mapper
public interface RequirementRevisionBusinessMapper extends BusinessMapper<RequirementAnalysisRevisionDO> {
    record VisibleRevisionPage(BusinessReadQuery page,Set<Long> managerProjects) {
        public VisibleRevisionPage { managerProjects=Set.copyOf(managerProjects); }
    }
    /** Private drafts require project-manager facts; frozen history remains visible to ordinary project readers. */
    default PageResult<RequirementAnalysisRevisionDO> selectVisibleRevisions(VisibleRevisionPage query) {
        return BusinessMapperQueries.page(this,query.page(),wrapper->wrapper.and(part->{
            part.ne("revision_state","DRAFT");
            if(!query.managerProjects().isEmpty())part.or(other->other.in("project_id",query.managerProjects()));
        }));
    }
}
