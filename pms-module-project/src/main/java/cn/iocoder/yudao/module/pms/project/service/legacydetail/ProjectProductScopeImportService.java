package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import java.util.*;
/** Shared command preparation for initial migration and approved baseline consolidation. */
@Service @RequiredArgsConstructor
public class ProjectProductScopeImportService {
    private final ProjectMasterMapper projects;
    private final ProjectLegacyNativeEvidenceService evidence;
    public DppmsProjectProductScopeApi.Command command(Long projectId,String sourceProjectKey,List<cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO> rows,ProjectAccessActor actor,boolean requireBaseline) {
        var project=projects.selectById(projectId);
        if(project==null || !Objects.equals(project.getTenantId(),actor.tenantId()))throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_PROJECT_INVALID");
        if(rows.isEmpty())return new DppmsProjectProductScopeApi.Command(actor.tenantId(),actor.actorId(),projectId,project.getVersion(),sourceProjectKey,List.of());
        Long snapshotId=rows.getFirst().getSnapshotId();
        var statuses=evidence.statuses(actor.tenantId(),sourceProjectKey,snapshotId,rows);
        var entries=new ArrayList<DppmsProjectProductScopeApi.Entry>();
        Set<String> keys=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(var row:rows) {
            var raw=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});
            if(snapshotId==null || !Objects.equals(row.getSnapshotId(),snapshotId)
                    || !Objects.equals(row.getTenantId(),actor.tenantId()) || !"products".equals(row.getDomainCode())
                    || !"pm_project_product_line".equals(row.getSourceTable()) || !keys.add(row.getSourceKey())
                    || raw==null || !raw.keySet().equals(Set.copyOf(DppmsProjectProductScopeApi.ORIGINAL_COLUMNS))
                    || !Objects.equals(raw.get("id"),row.getSourceKey()) || !Objects.equals(raw.get("projectId"),sourceProjectKey))
                throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_ORIGINAL_IDENTITY_INVALID");
            var status=statuses.get(row.getSourceKey());
            if(status==null || Set.of("CHECKSUM_CONFLICT","AMBIGUOUS","RETAINED").contains(status.classification()))
                throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_EVIDENCE_INVALID");
            var baseline=status.scopeBaseline();
            if(baseline==null) {
                if(requireBaseline || !status.targets().isEmpty())throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_BASELINE_MISSING");
                entries.add(new DppmsProjectProductScopeApi.Entry(row.getSourceKey(),raw,null));continue;
            }
            var targets=status.targets();
            if(!"MAPPED".equals(status.classification()) || targets.size()!=1 || !"COM".equals(targets.getFirst().targetContext())
                    || !"DeliveryScope".equals(targets.getFirst().targetObjectType()) || !"com_delivery_scope".equals(targets.getFirst().targetTable())
                    || !"BUSINESS_ENTITY".equals(targets.getFirst().targetRole()) || targets.getFirst().targetSequence()!=0
                    || baseline.id()==null || baseline.id()<=0 || !Objects.equals(targets.getFirst().targetId(),baseline.id())
                    || !ids.add(baseline.id()) || baseline.createTime()==null || baseline.updateTime()==null)
                throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_BASELINE_INVALID");
            // Business values are revalidated by COM against its formal scope and order entities.
            entries.add(new DppmsProjectProductScopeApi.Entry(row.getSourceKey(),raw,baseline));
        }
        return new DppmsProjectProductScopeApi.Command(actor.tenantId(),actor.actorId(),projectId,project.getVersion(),sourceProjectKey,entries);
    }
}
