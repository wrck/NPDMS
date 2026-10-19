package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import jakarta.validation.constraints.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Reconciles adjudicated installation facts through IMP and the existing PLT migration ledger. */
@Service @RequiredArgsConstructor
public class ProjectInstallationRecordReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final DppmsInstallationRecordMigrationApi installations;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    public record InstallationRequest(@NotNull @Positive Long sourceId,@NotNull @Positive Long snapshotId,
            @NotBlank @Pattern(regexp="[0-9a-f]{64}") String checksum,
            @NotNull @Size(min=1,max=1000) List<@NotBlank @Pattern(regexp="[0-9]{1,32}") String> sourceKeys) {}
    private record Prepared(Loaded loaded,List<ProjectLegacyRecordDO> rows,List<DppmsInstallationRecordMigrationApi.Command> parts) {}
    private Prepared prepare(Long projectId,InstallationRequest request,ProjectAccessActor actor) {
        if(request==null || request.sourceKeys()==null || request.sourceKeys().isEmpty() || request.sourceKeys().size()>1000
                || new HashSet<>(request.sourceKeys()).size()!=request.sourceKeys().size()
                || request.sourceKeys().stream().anyMatch(k->k==null || !k.matches("[0-9]{1,32}")))
            throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_REQUEST_INVALID");
        var loaded=sources.loadCorrectionSource(projectId,new Request(request.sourceId(),request.snapshotId(),request.checksum()),actor);
        var selected=new HashSet<>(request.sourceKeys());
        var rows=loaded.domains().get("installations").stream().filter(r->selected.contains(r.getSourceKey()))
                .sorted(Comparator.comparing(ProjectLegacyRecordDO::getSourceKey)).toList();
        if(rows.size()!=selected.size())throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_SOURCE_COVERAGE_INVALID");
        var statuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),request.snapshotId(),rows);
        var entries=new ArrayList<DppmsInstallationRecordMigrationApi.Entry>();Set<Long> ids=new HashSet<>();
        for(var row:rows) {
            var raw=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});
            var status=statuses.get(row.getSourceKey());
            if(raw==null || !raw.keySet().equals(Set.copyOf(DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS))
                    || !Objects.equals(raw.get("id"),row.getSourceKey()) || !Objects.equals(raw.get("projectId"),loaded.source().getSourceProjectKey())
                    || status==null || !"MAPPED".equals(status.classification()) || status.installationBaseline()==null)
                throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_SOURCE_OR_EVIDENCE_INVALID");
            var baseline=status.installationBaseline();var targets=status.targets();
            if(baseline.id()==null || baseline.id()<=0 || !ids.add(baseline.id()) || targets.size()!=1
                    || !"IMP".equals(targets.getFirst().targetContext()) || !"Installation".equals(targets.getFirst().targetObjectType())
                    || !"imp_eng_installation".equals(targets.getFirst().targetTable()) || !"BUSINESS_ENTITY".equals(targets.getFirst().targetRole())
                    || targets.getFirst().targetSequence()!=0 || !Objects.equals(targets.getFirst().targetId(),baseline.id()))
                throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_BASELINE_INVALID");
            entries.add(new DppmsInstallationRecordMigrationApi.Entry(row.getSourceKey(),raw,baseline));
        }
        var parts=new ArrayList<DppmsInstallationRecordMigrationApi.Command>();
        for(int offset=0;offset<entries.size();offset+=100)parts.add(new DppmsInstallationRecordMigrationApi.Command(
                actor.tenantId(),actor.actorId(),projectId,loaded.source().getSourceProjectKey(),entries.subList(offset,Math.min(offset+100,entries.size()))));
        return new Prepared(loaded,rows,parts);
    }
    @Transactional(rollbackFor=Exception.class)
    public List<DppmsInstallationRecordMigrationApi.Result> preview(Long projectId,InstallationRequest request,ProjectAccessActor actor) {
        var prepared=prepare(projectId,request,actor);var results=new ArrayList<DppmsInstallationRecordMigrationApi.Result>();
        prepared.parts().forEach(part->results.addAll(installations.preview(part)));return List.copyOf(results);
    }
    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult reconcile(Long projectId,InstallationRequest request,ProjectAccessActor actor) {
        var prepared=prepare(projectId,request,actor);
        String key="DPPMS-INSTALLATION-RECORD:V1:"+DigestUtil.sha256Hex(JsonUtils.toJsonString(
                List.of(request.sourceId().toString(),request.snapshotId().toString(),request.sourceKeys().stream().sorted().toList())));
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request.sourceId(),request.snapshotId(),request.checksum(),request.sourceKeys().stream().sorted().toList())));
        // Revalidate before replay, so a completed command never conceals a subsequent business change.
        prepared.parts().forEach(installations::preview);
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:INSTALLATION_RECORD",0L,key),digest,CorrectionReport.class,()->{
            var outcomes=new LinkedHashMap<String,NativeEntityMigrationRow>();var targets=new ArrayList<CorrectionTarget>();
            var baselines=new LinkedHashMap<String,DppmsInstallationRecordMigrationApi.Baseline>();
            Set<String> expected=new HashSet<>(request.sourceKeys());Set<Long> ids=new HashSet<>();
            for(var part:prepared.parts())for(var applied:installations.apply(part)) {
                var entry=part.entries().stream().filter(e->e.sourceKey().equals(applied.sourceKey())).findFirst().orElseThrow();
                if(!expected.remove(applied.sourceKey()) || !Objects.equals(applied.targetId(),entry.baseline().id())
                        || !ids.add(applied.targetId()) || !Set.of("UPDATED","UNCHANGED").contains(applied.action())
                        || applied.after()==null || !Objects.equals(applied.after().id(),applied.targetId()))
                    throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_OWNER_RESULT_INVALID");
                var target=new ExternalTargetMapping("IMP","Installation","imp_eng_installation",applied.targetId(),"BUSINESS_ENTITY",0);
                outcomes.put("installations:"+applied.sourceKey(),NativeEntityMigrationRow.mapped("installations",applied.sourceKey(),target));
                targets.add(new CorrectionTarget(applied.sourceKey(),List.of(target)));baselines.put(applied.sourceKey(),applied.after());
            }
            if(!expected.isEmpty())throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_OWNER_COVERAGE_INVALID");
            evidence.reconcile(prepared.loaded().source(),prepared.loaded().snapshot(),"installations",prepared.rows(),outcomes,"INSTALLATION_RECORD_V2",Map.of(),baselines);
            return new CorrectionReport(request.sourceId(),request.snapshotId(),"installations",prepared.rows().size(),targets);
        },report->new SuccessFacts("PROJ_DPPMS_INSTALLATION_RECORD_RECONCILE","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS)
            throw new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_"+result.decision());
        return new CorrectionResult(result.decision().name(),result.response());
    }
}
