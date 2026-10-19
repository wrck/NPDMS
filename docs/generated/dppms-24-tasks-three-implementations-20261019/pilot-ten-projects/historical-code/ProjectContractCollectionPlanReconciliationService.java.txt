package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.ContractCollectionPlanSyncProofApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Links immutable project evidence to already migrated formal plans; never writes COM or INT tables. */
@Service
@RequiredArgsConstructor
public class ProjectContractCollectionPlanReconciliationService {
    private final ProjectLegacyEntityMigrationService migration;
    private final ContractCollectionPlanSyncProofApi proofs;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;

    public record Request(@NotNull @Positive Long sourceId, @NotNull @Positive Long snapshotId,
                          @NotBlank @Pattern(regexp="[0-9a-f]{64}") String checksum,
                          @NotNull @Positive Long syncTaskId) {}

    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult reconcile(Long projectId, Request request, ProjectAccessActor actor) {
        var loaded = migration.loadCorrectionSource(projectId,
                new ProjectLegacyEntityMigrationService.Request(request.sourceId(),request.snapshotId(),request.checksum()),actor);
        var rows = loaded.domains().get("plans");
        if (rows.isEmpty()) throw new IllegalArgumentException("COLLECTION_PLAN_SOURCE_EMPTY");
        var verified = new ArrayList<ContractCollectionPlanSyncProofApi.Proof>();
        for (int offset=0; offset<rows.size(); offset+=1000) {
            var part=rows.subList(offset,Math.min(offset+1000,rows.size()));
            verified.addAll(proofs.verify(new ContractCollectionPlanSyncProofApi.Query(actor.tenantId(),actor.actorId(),
                    request.syncTaskId(),part.stream().map(row->row.getSourceKey()).toList())));
        }
        Map<String,ContractCollectionPlanSyncProofApi.Proof> bySource = new HashMap<>();
        Set<Long> targetIds = new HashSet<>();
        for (var proof:verified) {
            if (proof == null || proof.sourceKey() == null || proof.targetId() == null || proof.targetId()<=0
                    || !targetIds.add(proof.targetId()) || bySource.put(proof.sourceKey(),proof)!=null)
                throw new IllegalStateException("COLLECTION_PLAN_SYNC_PROOF_INVALID");
        }
        Map<String,NativeEntityMigrationRow> outcomes = new LinkedHashMap<>();
        var targets = new ArrayList<CorrectionTarget>();
        for (var row:rows) {
            var proof=bySource.get(row.getSourceKey());
            Map<String,String> original=JsonUtils.parseObject(row.getPayloadJson(),Map.class);
            if (proof == null || !sameOriginal(original,proof.originalFields()))
                throw new IllegalStateException("COLLECTION_PLAN_SNAPSHOT_DIFFERS_FROM_SYNC");
            var target=new ExternalTargetMapping("COM","ContractCollectionPlan","com_contract_collection_plan",proof.targetId(),"BUSINESS_ENTITY",0);
            outcomes.put("plans:"+row.getSourceKey(),NativeEntityMigrationRow.mapped("plans",row.getSourceKey(),target));
            targets.add(new CorrectionTarget(row.getSourceKey(),List.of(target)));
        }
        if (bySource.size()!=rows.size()) throw new IllegalStateException("COLLECTION_PLAN_SYNC_PROOF_INVALID");
        String key="DPPMS-COLLECTION-PLAN-CORRECTION:"+request.sourceId()+":"+request.snapshotId()+":"+request.syncTaskId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request,targets)));
        // Proof is checked before replaying a cached result, so stale mappings cannot appear current.
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:COLLECTION_PLAN_CORRECTION",0L,key),
                digest,CorrectionReport.class,()->{
                    evidence.reconcile(loaded.source(),loaded.snapshot(),"plans",rows,outcomes,"COLLECTION_PLAN_V1");
                    return new CorrectionReport(request.sourceId(),request.snapshotId(),"plans",rows.size(),targets);
                },report->new SuccessFacts("PROJ_DPPMS_COLLECTION_PLAN_RECONCILE","Project",projectId.toString(),key,
                        JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"syncTaskId",request.syncTaskId(),"report",report)),null,null));
        if (result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS)
            throw new IllegalStateException("COLLECTION_PLAN_CORRECTION_"+result.decision());
        return new CorrectionResult(result.decision().name(),result.response());
    }

    private static final Set<String> DATES=Set.of("eventPlanHappenDate","eventActualFinishDate","createTime","updateTime","effectiveFrom","effectiveTo");
    static boolean sameOriginal(Map<String,String> snapshot, Map<String,String> synced) {
        if (snapshot == null || synced == null || synced.size()!=16 || !snapshot.keySet().containsAll(synced.keySet())) return false;
        for (var entry:synced.entrySet()) {
            String old=snapshot.get(entry.getKey()), next=entry.getValue();
            if (DATES.contains(entry.getKey())) {
                if (!Objects.equals(old==null?null:time(old),next==null?null:time(next))) return false;
            } else if ("afterDaysNum".equals(entry.getKey())) {
                if (!Objects.equals(old==null?null:Integer.valueOf(old),next==null?null:Integer.valueOf(next))) return false;
            } else if (!Objects.equals(old,next)) return false;
        }
        return true;
    }
}
