package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.ProjectDeliverableSubmissionDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProjectDeliverableSubmissionMapper extends BaseMapperX<ProjectDeliverableSubmissionDO> {
    default ProjectDeliverableSubmissionDO selectRequest(Long tenantId, Long deliverableId, String requestKey) {
        return selectOne(new LambdaQueryWrapperX<ProjectDeliverableSubmissionDO>()
                .eq(ProjectDeliverableSubmissionDO::getTenantId, tenantId)
                .eq(ProjectDeliverableSubmissionDO::getDeliverableId, deliverableId)
                .eq(ProjectDeliverableSubmissionDO::getRequestKey, requestKey));
    }
    default ProjectDeliverableSubmissionDO selectSource(Long tenantId, Long sourceVersionId) {
        return selectOne(new LambdaQueryWrapperX<ProjectDeliverableSubmissionDO>()
                .eq(ProjectDeliverableSubmissionDO::getTenantId, tenantId)
                .eq(ProjectDeliverableSubmissionDO::getSourceVersionId, sourceVersionId));
    }
    default List<ProjectDeliverableSubmissionDO> selectHistory(HistoryQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectDeliverableSubmissionDO>()
                .eq(ProjectDeliverableSubmissionDO::getTenantId, query.tenantId())
                .eq(ProjectDeliverableSubmissionDO::getProjectId, query.projectId())
                .eq(ProjectDeliverableSubmissionDO::getDeliverableId, query.deliverableId())
                .orderByDesc(ProjectDeliverableSubmissionDO::getId));
    }
    record HistoryQuery(Long tenantId, Long projectId, Long deliverableId) { }
}
