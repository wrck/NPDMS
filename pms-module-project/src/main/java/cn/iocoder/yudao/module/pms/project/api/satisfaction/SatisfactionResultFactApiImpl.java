package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResultIdentityQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SatisfactionResultFactApiImpl implements SatisfactionResultFactApi {
    private final SatisfactionResultMapper resultMapper;

    @Override
    public SatisfactionResultFact inspect(SatisfactionResultFactQuery query) {
        Long tenantId = trustedTenant(query);
        return toFact(resultMapper.selectFact(new SatisfactionResultIdentityQuery(tenantId, query.resultId())), query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SatisfactionResultFact lockAndRevalidate(SatisfactionResultFactQuery query) {
        Long tenantId = trustedTenant(query);
        return toFact(resultMapper.selectFactForUpdate(
                new SatisfactionResultIdentityQuery(tenantId, query.resultId())), query);
    }

    private Long trustedTenant(SatisfactionResultFactQuery query) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (query == null || query.resultId() == null || query.resultId() <= 0
                || query.expectedFactVersion() == null || query.expectedFactVersion() < 0
                || !Objects.equals(query.tenantId(), tenantId)) {
            throw new IllegalArgumentException("invalid satisfaction result fact query");
        }
        return tenantId;
    }

    private SatisfactionResultFact toFact(SatisfactionResultFactRecord row, SatisfactionResultFactQuery query) {
        if (row == null) return unavailable("NOT_FOUND");
        if (!Objects.equals(row.factVersion(), query.expectedFactVersion())) return unavailable("VERSION_CONFLICT");
        return new SatisfactionResultFact("FOUND", row.collectionKey(), row.taskId(), row.taskRevisionNo(),
                row.questionnaireId(), row.responseId(), row.resultId(), row.resultVersion(),
                row.templateRevisionId(), row.ruleVersion(), row.threshold(), row.sourceOwnerContext(),
                row.sourceObjectType(), row.sourceObjectId(), row.sourceObjectVersion(),
                Boolean.TRUE.equals(row.passed()), row.resultStatus(), row.archiveStatus(), row.factVersion());
    }

    private SatisfactionResultFact unavailable(String outcome) {
        return new SatisfactionResultFact(outcome, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null, null, null);
    }
}
