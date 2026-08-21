package cn.iocoder.yudao.module.pms.project.service.template;

import cn.iocoder.yudao.module.pms.project.domain.template.ProjectTemplateRevisionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateCandidateResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCriteria;

public interface ProjectTemplateCandidateService {

    TemplateCandidateResult findCandidates(long tenantId, long actorId, TemplateMatchCriteria criteria);

    ProjectTemplateRevisionSnapshot getPreview(long tenantId, long actorId, long revisionId,
                                               TemplateMatchCriteria criteria);

    ProjectTemplateRevisionSnapshot resolveForCreate(long tenantId, long actorId, Long selectedRevisionId,
                                                      TemplateMatchCriteria criteria, String candidateWatermark);
}
