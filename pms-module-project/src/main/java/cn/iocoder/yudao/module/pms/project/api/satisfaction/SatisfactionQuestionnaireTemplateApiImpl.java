package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateResolveQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionTemplateCandidateRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateCandidateQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;

@Service
@RequiredArgsConstructor
public class SatisfactionQuestionnaireTemplateApiImpl implements SatisfactionQuestionnaireTemplateApi {

    private final SatisfactionQuestionnaireTemplateMapper templateMapper;

    @Override
    public SatisfactionTemplateFact resolvePublished(SatisfactionTemplateResolveQuery query) {
        Long tenantId = trustedTenantId();
        if (query == null || !Objects.equals(query.tenantId(), tenantId)
                || blank(query.projectType()) || blank(query.signingMode())
                || blank(query.implementationMode()) || blank(query.businessPurposeCode())
                || blank(query.applicableTimingCode())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        List<SatisfactionTemplateCandidateRecord> candidates = templateMapper.selectPublishedCandidates(
                new SatisfactionTemplateCandidateQuery(tenantId, query.projectType(), query.signingMode(),
                        query.implementationMode(), query.businessPurposeCode(), query.applicableTimingCode()));
        if (candidates == null || candidates.isEmpty()) {
            return new SatisfactionTemplateFact("NO_MATCH", null, null, null, null, null);
        }
        SatisfactionTemplateCandidateRecord first = candidates.getFirst();
        if (candidates.size() > 1 && Objects.equals(first.priority(), candidates.get(1).priority())) {
            return new SatisfactionTemplateFact("AMBIGUOUS", null, null, null, null, null);
        }
        if (first.templateId() == null || first.templateRevisionId() == null || first.templateVersion() == null
                || first.templateVersion() <= 0 || blank(first.ruleVersion()) || first.threshold() == null) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return new SatisfactionTemplateFact("FOUND", first.templateId(), first.templateRevisionId(),
                first.templateVersion(), first.ruleVersion(), first.threshold());
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return tenantId;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
