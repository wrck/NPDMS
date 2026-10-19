package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi.Baseline;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Reuses captured full raw rows and immutable successful bindings; adds an independent correction receipt. */
@Service @RequiredArgsConstructor
public class ProjectSoftwareHistoryCorrectionService {
    private final ProjectLegacyEntityMigrationService sources;
    private final DppmsSoftwareHistoryCorrectionApi correction;
    private final DppmsAssetSourceHistoryApi history;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    public record CorrectionRequest(@NotNull @Valid Request source,@NotNull @Size(min=1,max=2000) List<Baseline> baselines) {}

    @Transactional(rollbackFor=Exception.class)
    public List<Baseline> preview(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        return correction.preview(prepare(projectId,loaded,actor,request));
    }
    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult correct(Long projectId,CorrectionRequest request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request.source(),actor);
        var source=prepare(projectId,loaded,actor,request.source());
        String key="DPPMS-SOFTWARE-HISTORY:"+request.source().sourceId()+":"+request.source().snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request)));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:SOFTWARE_HISTORY_CORRECTION",0L,key),
                digest,CorrectionReport.class,()->{
                    var results=correction.correct(new DppmsSoftwareHistoryCorrectionApi.Correction(source,request.baselines()));
                    Map<String,NativeEntityMigrationRow> mapped=new LinkedHashMap<>();
                    var targets=new ArrayList<CorrectionTarget>();
                    for (var row:results) {
                        mapped.put(row.domain()+":"+row.sourceKey(),row);
                        targets.add(new CorrectionTarget(row.domain()+":"+row.sourceKey(),row.targets()));
                    }
                    evidence.reconcile(loaded.source(),loaded.snapshot(),"software-changes",loaded.domains().get("software-changes"),mapped,"SOFTWARE_BATCH_V1");
                    evidence.reconcile(loaded.source(),loaded.snapshot(),"software-versions",loaded.domains().get("software-versions"),mapped,"SOFTWARE_HISTORY_V1");
                    return new CorrectionReport(request.source().sourceId(),request.source().snapshotId(),"software-history",targets.size(),targets);
                },report->new SuccessFacts("PROJ_DPPMS_SOFTWARE_HISTORY_CORRECT","Project",projectId.toString(),key,
                        JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if (execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)
            throw new IllegalArgumentException("DPPMS_SOFTWARE_CORRECTION_"+execution.decision());
        // A completed command remains immutable; replay still validates the formal rows and eight properties.
        if (execution.decision()==Decision.REPLAY_COMPLETED) history.importSourceHistory(source);
        return new CorrectionResult(execution.decision().name(),execution.response());
    }
    private DppmsAssetSourceHistoryApi.Command prepare(Long projectId,Loaded loaded,ProjectAccessActor actor,Request request) {
        var observations=sources.softwareObservations(loaded,actor,request.softwareHistoryDefinitionId(),true);
        var batches=sources.softwareBatches(loaded,actor);
        if (observations.stream().anyMatch(row->row.mappedRecordId()==null) || batches.stream().anyMatch(row->row.mappedRecordId()==null))
            throw new IllegalArgumentException("DPPMS_SOFTWARE_CORRECTION_SUCCESSFUL_BINDING_REQUIRED");
        return new DppmsAssetSourceHistoryApi.Command(actor.tenantId(),actor.actorId(),projectId,loaded.source().getSourceProjectKey(),
                loaded.snapshot().getChecksum(),loaded.snapshot().getSourceReadAt(),observations,batches,List.of());
    }
}
