package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectTemplateInitializationMapper {
    boolean hasRuntime(@Param("query") ProjectPlanScopeQuery query);

    int bindIfUninitialized(@Param("query") Binding query);

    record Binding(Long tenantId, Long projectId, Long expectedVersion, Long templateId,
                   Long revisionId, Integer revisionNo, String processDefinitionKey,
                   String processDefinitionVersion, String closurePolicySnapshot, String updater) { }
}
