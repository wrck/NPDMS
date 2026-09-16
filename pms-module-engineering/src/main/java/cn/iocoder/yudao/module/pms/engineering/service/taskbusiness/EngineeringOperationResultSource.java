package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.OwnerOperationResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** Reads the result just written in the Owner transaction; never invents a success event for a missing record. */
@Component
@RequiredArgsConstructor
public class EngineeringOperationResultSource implements OwnerOperationResultSource {
    private final ObjectProvider<SiteSurveyEntityMapper> surveys;
    private final ObjectProvider<RequirementAnalysisMapper> requirements;
    @Override public boolean supports(String aggregate) { return "SiteSurvey".equals(aggregate) || "RequirementAnalysis".equals(aggregate); }
    @Override public ProjectOperationResult current(Long tenant, Long project, String aggregate, Long objectId) {
        if (!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()) || project == null || objectId == null)
            throw new IllegalArgumentException("OWNER_RESULT_CONTEXT_INVALID");
        if ("SiteSurvey".equals(aggregate)) {
            var row = surveys.getObject().selectById(objectId);
            if (row == null || Boolean.TRUE.equals(row.getDeleted())) return null;
            if (!Objects.equals(row.getTenantId(),tenant) || !Objects.equals(row.getProjectId(),project)
                    || row.getVersion() == null || row.getStatus() == null || row.getStatus() < 0 || row.getStatus() > 3)
                throw new IllegalArgumentException("OWNER_RESULT_IDENTITY_INVALID");
            String code = switch (row.getStatus()) {
                case 1 -> "SURVEY_CONFIRMED";
                case 2 -> "SURVEY_REJECTED";
                case 3 -> "SURVEY_ARCHIVED";
                default -> "SURVEY_DRAFT_SAVED";
            };
            return new ProjectOperationResult("SOL","SITE_SURVEY",objectId.toString(),null,row.getVersion(),
                    "SOL:SITE_SURVEY:" + objectId + ":" + row.getVersion() + ":" + row.getStatus(),code,null,false);
        }
        if ("RequirementAnalysis".equals(aggregate)) {
            var row = requirements.getObject().selectRevision(new RequirementRevisionQuery(tenant,objectId));
            if (row == null) return null;
            if (!Objects.equals(row.getTenantId(),tenant) || !Objects.equals(row.getProjectId(),project)
                    || row.getVersion() == null || !java.util.Set.of("DRAFT","FROZEN").contains(String.valueOf(row.getRevisionState())))
                throw new IllegalArgumentException("OWNER_RESULT_IDENTITY_INVALID");
            return new ProjectOperationResult("SOL","REQUIREMENT_ANALYSIS",objectId.toString(),objectId.toString(),row.getVersion(),
                    "SOL:REQUIREMENT_ANALYSIS_REVISION:" + objectId + ":" + row.getVersion() + ":" + row.getRevisionState(),
                    "FROZEN".equals(row.getRevisionState()) ? "REQUIREMENT_ANALYSIS_COMPLETED" : "REQUIREMENT_ANALYSIS_DRAFT_SAVED",null,false);
        }
        return null;
    }
}
