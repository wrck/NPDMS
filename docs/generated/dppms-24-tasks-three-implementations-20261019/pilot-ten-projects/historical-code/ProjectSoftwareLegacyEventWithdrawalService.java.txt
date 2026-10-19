package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Uses both the current complete carry and the immutable V3 receipt; neither may be replaced by client IDs. */
@Service
@RequiredArgsConstructor
public class ProjectSoftwareLegacyEventWithdrawalService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectLegacyDetailMapper legacy;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final DppmsSoftwareLegacyEventWithdrawalApi owner;
    private final PlatformCommandExecutionApi commands;
    public record PreviewRequest(@NotNull @Valid Request source,@NotNull @Positive Long legacySnapshotId) {}
    public record WithdrawalRequest(@NotNull @Valid PreviewRequest source,
                                    @NotNull @Size(min=1,max=2000) List<Baseline> baselines) {}
    public record Report(Long sourceId,Long snapshotId,Long legacySnapshotId,int eventCount,List<Baseline> withdrawn) {}
    public record Result(String decision,Report report) {}
    record Prepared(DppmsAssetSourceHistoryApi.Command source,List<Identity> identities) {}

    @Transactional(rollbackFor=Exception.class)
    public List<Baseline> preview(Long projectId,PreviewRequest request,ProjectAccessActor actor) {
        var prepared=prepare(projectId,request,actor);
        return owner.preview(prepared.source(),prepared.identities());
    }

    @Transactional(rollbackFor=Exception.class)
    public Result withdraw(Long projectId,WithdrawalRequest request,ProjectAccessActor actor) {
        var prepared=prepare(projectId,request.source(),actor);
        if(request.baselines()==null || request.baselines().stream().anyMatch(Objects::isNull)
                || !prepared.identities().equals(request.baselines().stream().map(Baseline::identity).toList()))
            fail("SUCCESSFUL_BINDINGS_CHANGED");
        String key="DPPMS-SOFTWARE-EVENT-WITHDRAWAL:"+request.source().source().sourceId()+":"
                +request.source().source().snapshotId()+":"+request.source().legacySnapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request)));
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:SOFTWARE_EVENT_WITHDRAWAL",0L,key),
                digest,Report.class,()->{
                    owner.withdraw(new Withdrawal(prepared.source(),request.baselines()));
                    return new Report(request.source().source().sourceId(),request.source().source().snapshotId(),
                            request.source().legacySnapshotId(),request.baselines().size(),List.copyOf(request.baselines()));
                },report->new SuccessFacts("PROJ_DPPMS_SOFTWARE_EVENT_WITHDRAW","Project",projectId.toString(),key,
                        JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS) fail(result.decision().name());
        if(result.decision()==Decision.REPLAY_COMPLETED) owner.verifyWithdrawn(prepared.source(),prepared.identities());
        return new Result(result.decision().name(),result.response());
    }

    Prepared prepare(Long projectId,PreviewRequest request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request.source(),actor);
        var observations=sources.softwareObservations(loaded,actor,request.source().softwareHistoryDefinitionId());
        var batches=sources.softwareBatches(loaded,actor);
        if(observations.isEmpty() || observations.stream().anyMatch(row->row.mappedRecordId()==null)
                || batches.stream().anyMatch(row->row.mappedRecordId()==null)) fail("FORMAL_CARRY_REQUIRED");
        var snapshot=legacy.selectSnapshot(new ProjectLegacyDetailMapper.SnapshotQuery(actor.tenantId(),request.legacySnapshotId()));
        if(snapshot==null || !Objects.equals(snapshot.getTenantId(),actor.tenantId())
                || !Objects.equals(snapshot.getSourceId(),loaded.source().getId())) fail("LEGACY_SNAPSHOT_FORBIDDEN");
        var originals=new ArrayList<ProjectLegacyRecordDO>();
        for(int offset=0;;offset+=500) {
            var page=legacy.selectRecords(new ProjectLegacyDetailMapper.RecordsPageQuery(actor.tenantId(),snapshot.getId(),
                    "software-versions",null,null,null,offset,500));
            originals.addAll(page);if(page.size()<500)break;
        }
        var statuses=evidence.legacySoftwareEventStatuses(actor.tenantId(),loaded.source().getSourceProjectKey(),snapshot.getId(),originals);
        var carried=new HashMap<String,DppmsAssetSourceHistoryApi.SoftwareObservation>();
        observations.forEach(row->carried.put(row.sourceId().toString(),row));
        var identities=new ArrayList<Identity>();var ids=new HashSet<Long>();
        for(var row:originals) {
            var status=statuses.get(row.getSourceKey());
            if(status==null || !Set.of("MAPPED","RETAINED").contains(status.classification())) fail("LEGACY_EVIDENCE_INVALID");
            if(status.targets().isEmpty()) {
                if(!"RETAINED".equals(status.classification())) fail("LEGACY_MAPPING_MISSING");
                continue;
            }
            var matches=status.targets().stream().filter(target->"ast_device_network_version_event".equals(target.targetTable())).toList();
            var formal=carried.get(row.getSourceKey());
            if(matches.size()!=1 || formal==null || !"AST".equals(matches.getFirst().targetContext())
                    || !"DeviceNetworkVersionEvent".equals(matches.getFirst().targetObjectType())
                    || !"SOFTWARE_EVENT".equals(matches.getFirst().targetRole()) || matches.getFirst().targetSequence()!=0
                    || matches.getFirst().targetId()==null || !ids.add(matches.getFirst().targetId())) fail("LEGACY_BINDING_INVALID");
            identities.add(new Identity(formal.sourceId(),matches.getFirst().targetId(),formal.mappedRecordId()));
        }
        if(identities.isEmpty()) fail("EMPTY_WITHDRAWAL");
        var source=new DppmsAssetSourceHistoryApi.Command(actor.tenantId(),actor.actorId(),projectId,loaded.source().getSourceProjectKey(),
                loaded.snapshot().getChecksum(),loaded.snapshot().getSourceReadAt(),observations,batches,List.of());
        return new Prepared(source,List.copyOf(identities));
    }
    private static void fail(String reason) {throw new IllegalArgumentException("DPPMS_SOFTWARE_EVENT_WITHDRAWAL_"+reason);}
}
