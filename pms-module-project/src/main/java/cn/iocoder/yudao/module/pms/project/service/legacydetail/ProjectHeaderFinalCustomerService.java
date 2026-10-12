package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.SourceIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.PartySourceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.HeaderFinalCustomerQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** G001/20: the product's actual end user is a formal project party, not the order buyer. */
@Service
@RequiredArgsConstructor
public class ProjectHeaderFinalCustomerService {
    private final ProjectManualCreationService access;
    private final ProjectLegacyDetailMapper sources;
    private final ProjectPartyMapper parties;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ExternalTargetMapping importHeader(Long projectId, ProjectLegacyRecordDO header, ProjectAccessActor actor) {
        validateActor(actor);
        access.getProjectForManage(projectId, actor);
        if (header == null || !"pm_project_header".equals(header.getSourceTable()) || header.getSourceKey() == null)
            throw new IllegalArgumentException("DPPMS_FINAL_CUSTOMER_HEADER_IDENTITY_INVALID");
        Map<String, String> values = JsonUtils.parseObject(header.getPayloadJson(), new TypeReference<Map<String, String>>() {});
        if (!Objects.equals(header.getSourceKey(), values.get("projectId")))
            throw new IllegalArgumentException("DPPMS_FINAL_CUSTOMER_HEADER_IDENTITY_INVALID");
        var binding = sources.selectSourceForUpdate(new SourceIdentityQuery(actor.tenantId(), header.getSourceKey()));
        if (binding == null || !Objects.equals(binding.getProjectId(), projectId)
                || !Objects.equals(binding.getCurrentSnapshotId(), header.getSnapshotId()))
            throw new IllegalArgumentException("DPPMS_FINAL_CUSTOMER_SOURCE_BINDING_CONFLICT");
        String name = values.get("column013");
        if (name == null || name.isBlank()) return null;
        // Project validity belongs to proj_project; it does not delete the actual end-user fact.
        LocalDateTime effectiveFrom = time(values.get("effectiveFrom"));
        LocalDateTime effectiveTo = time(values.get("effectiveTo"));
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom))
            throw new IllegalArgumentException("DPPMS_PROJECT_VALIDITY_INTERVAL_INVALID");
        var identity = new PartySourceQuery(actor.tenantId(), "DPPMS", "pm_project_header", header.getSourceKey(), "FINAL_CUSTOMER");
        var existing = parties.selectSourceForUpdate(identity);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getDeleted()) || !Objects.equals(existing.getProjectId(), projectId)
                    || !Objects.equals(existing.getPartyName(), name)
                    || !Objects.equals(existing.getEffectiveFrom(), effectiveFrom)
                    || !Objects.equals(existing.getEffectiveTo(), effectiveTo)
                    || !"ACTIVE".equals(existing.getStatus()))
                throw new IllegalArgumentException("DPPMS_FINAL_CUSTOMER_EXISTING_PARTY_CONFLICT");
            return mapping(existing);
        }
        var party = new ProjectPartyDO();
        party.setId(IdWorker.getId());
        party.setTenantId(actor.tenantId());
        party.setProjectId(projectId);
        party.setPartyRole("FINAL_CUSTOMER");
        // A name is not sufficient evidence for a current customer-master code.
        party.setPartyName(name);
        party.setSourceSystem("DPPMS");
        party.setSourceTable("pm_project_header");
        party.setSourceRecordKey(header.getSourceKey());
        party.setEffectiveFrom(effectiveFrom);
        party.setEffectiveTo(effectiveTo);
        party.setStatus("ACTIVE");
        party.setVersion(0);
        if (parties.insert(party) != 1)
            throw new IllegalStateException("DPPMS_FINAL_CUSTOMER_INSERT_FAILED");
        return mapping(party);
    }

    public List<String> readNames(Long projectId, ProjectAccessActor actor) {
        validateActor(actor);
        access.getProject(projectId, actor);
        return parties.selectHeaderFinalCustomers(new HeaderFinalCustomerQuery(actor.tenantId(), projectId)).stream()
                .map(ProjectPartyDO::getPartyName).toList();
    }

    private static ExternalTargetMapping mapping(ProjectPartyDO row) {
        return new ExternalTargetMapping("PROJ", "ProjectParty", "proj_project_party", row.getId(), "PARTY", 0);
    }

    private static LocalDateTime time(String value) {
        return value == null ? null : LocalDateTime.parse(value.replace(' ', 'T'));
    }

    private static void validateActor(ProjectAccessActor actor) {
        if (actor == null || actor.actorId() == null || actor.actorId() <= 0
                || !Objects.equals(actor.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DPPMS_FINAL_CUSTOMER_FORBIDDEN");
    }
}
