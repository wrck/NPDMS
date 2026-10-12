package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Resolve exact immutable migration targets before invoking the AST factory-record Owner. */
@Service @RequiredArgsConstructor
public class ProjectFactoryRecordImportService {
    private final ProjectLegacyNativeEvidenceService evidence;
    public DppmsAssetSourceHistoryApi.Command command(Long projectId,Loaded loaded,ProjectAccessActor actor) {
        var rows=loaded.domains().get("factory-versions");
        if(rows.isEmpty())return new DppmsAssetSourceHistoryApi.Command(actor.tenantId(),actor.actorId(),projectId,loaded.source().getSourceProjectKey(),
                loaded.snapshot().getChecksum(),loaded.snapshot().getSourceReadAt(),List.of(),List.of(),List.of());
        var statuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),loaded.snapshot().getId(),rows);
        var entries=new ArrayList<DppmsAssetSourceHistoryApi.FactoryVersion>();
        Set<String> keys=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(var row:rows) {
            var raw=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});
            if(raw==null || !raw.keySet().equals(Set.of("serial_number","conp","boot","cpld","pcb"))
                    || row.getSourceKey()==null || !keys.add(row.getSourceKey())
                    || !Objects.equals(row.getTenantId(),actor.tenantId()) || !Objects.equals(row.getSnapshotId(),loaded.snapshot().getId())
                    || !"fb_soft_version".equals(row.getSourceTable()) || !"factory-versions".equals(row.getDomainCode()))
                throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_SOURCE_INVALID");
            var status=statuses.get(row.getSourceKey());
            if(status==null || Set.of("CHECKSUM_CONFLICT","AMBIGUOUS","RETAINED").contains(status.classification()))
                throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_EVIDENCE_INVALID");
            var targets=status.targets().stream().filter(t->"ast_device_factory_version_record".equals(t.targetTable())).toList();
            if(targets.size()>1 || (!targets.isEmpty() && (!"AST".equals(targets.getFirst().targetContext())
                    || !"DeviceFactoryVersionRecord".equals(targets.getFirst().targetObjectType())
                    || !"FACTORY_VERSION_SOURCE".equals(targets.getFirst().targetRole()) || targets.getFirst().targetSequence()!=0
                    || targets.getFirst().targetId()==null || targets.getFirst().targetId()<=0 || !ids.add(targets.getFirst().targetId()))))
                throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_MAPPING_INVALID");
            entries.add(new DppmsAssetSourceHistoryApi.FactoryVersion(row.getSourceKey(),raw.get("serial_number"),raw.get("conp"),raw.get("boot"),raw.get("cpld"),raw.get("pcb"),
                    targets.isEmpty()?null:targets.getFirst().targetId()));
        }
        return new DppmsAssetSourceHistoryApi.Command(actor.tenantId(),actor.actorId(),projectId,loaded.source().getSourceProjectKey(),
                loaded.snapshot().getChecksum(),loaded.snapshot().getSourceReadAt(),List.of(),List.of(),entries);
    }
}
