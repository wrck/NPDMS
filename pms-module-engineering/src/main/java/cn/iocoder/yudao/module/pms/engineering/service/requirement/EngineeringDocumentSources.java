package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class EngineeringDocumentSources implements FileDocumentSourceProvider {
    private final RequirementAnalysisMapper requirements;
    @Override public List<Descriptor> descriptors() { return List.of(
            new Descriptor("SOL.REQUIREMENT_DOCUMENT", "需求分析表单附件")); }
    @Override public Scope resolve(Long tenant, String owner, String type, String object, String purpose) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || !"SOL".equals(owner)
                || purpose == null || !purpose.startsWith(FormAttachmentPolicy.PURPOSE_PREFIX)) return null;
        Long id;
        try { id = Long.valueOf(object); } catch (RuntimeException invalid) { return null; }
        if ("REQUIREMENT_ANALYSIS_REVISION".equals(type)) {
            var row = requirements.selectRevision(new RequirementRevisionQuery(tenant, id));
            return row == null ? null : new Scope(row.getProjectId(), "SOL.REQUIREMENT_DOCUMENT");
        }
        return null;
    }
}
