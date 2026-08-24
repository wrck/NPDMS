package cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectattribute.ProjectTemplateMatchHistoryDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.query.ProjectTemplateMatchHistoryPageQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectTemplateMatchHistoryMapper extends BaseMapperX<ProjectTemplateMatchHistoryDO> {

    default ProjectTemplateMatchHistoryDO selectByOperationId(Long tenantId, String operationId) {
        return selectOne(new LambdaQueryWrapperX<ProjectTemplateMatchHistoryDO>()
                .eq(ProjectTemplateMatchHistoryDO::getTenantId, tenantId)
                .eq(ProjectTemplateMatchHistoryDO::getOperationId, operationId));
    }

    default ProjectTemplateMatchHistoryDO selectByIdempotencyKey(
            Long tenantId, Long projectId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<ProjectTemplateMatchHistoryDO>()
                .eq(ProjectTemplateMatchHistoryDO::getTenantId, tenantId)
                .eq(ProjectTemplateMatchHistoryDO::getProjectId, projectId)
                .eq(ProjectTemplateMatchHistoryDO::getIdempotencyKey, idempotencyKey));
    }

    default PageResult<ProjectTemplateMatchHistoryDO> selectPage(ProjectTemplateMatchHistoryPageQuery query) {
        var wrapper = new LambdaQueryWrapperX<ProjectTemplateMatchHistoryDO>()
                .eq(ProjectTemplateMatchHistoryDO::getTenantId, query.tenantId())
                .eq(ProjectTemplateMatchHistoryDO::getProjectId, query.projectId())
                .eqIfPresent(ProjectTemplateMatchHistoryDO::getTriggerType, query.triggerType())
                .eqIfPresent(ProjectTemplateMatchHistoryDO::getMatchResult, query.matchResult())
                .eqIfPresent(ProjectTemplateMatchHistoryDO::getImpactResult, query.impactResult())
                .geIfPresent(ProjectTemplateMatchHistoryDO::getOccurredAt, query.occurredAtBegin())
                .leIfPresent(ProjectTemplateMatchHistoryDO::getOccurredAt, query.occurredAtEnd());
        boolean ascending = Boolean.TRUE.equals(query.ascending());
        switch (query.orderBy()) {
            case "recordedAt" -> wrapper.orderBy(true, ascending, ProjectTemplateMatchHistoryDO::getRecordedAt);
            case "id" -> wrapper.orderBy(true, ascending, ProjectTemplateMatchHistoryDO::getId);
            default -> wrapper.orderBy(true, ascending, ProjectTemplateMatchHistoryDO::getOccurredAt);
        }
        wrapper.orderByDesc(ProjectTemplateMatchHistoryDO::getId);
        return selectPage(query.pageParam(), wrapper);
    }
}
