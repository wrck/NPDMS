package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Single immutable source capture only; continuous refresh has not been adjudicated. */
@Service @RequiredArgsConstructor
public class ProjectLeaseProductReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectLeaseProductImportService importer;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    public record Preview(String sourceKey,Long targetId,String action) {}

    @Transactional(rollbackFor=Exception.class)
    public List<Preview> preview(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        return prepare(projectId,loaded,actor).entries().stream().map(e->new Preview(e.sourceKey(),e.targetId(),
                e.targetId()==null?"CREATE":"UNCHANGED")).toList();
    }
    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult reconcile(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        // Every replay checks the formal record including its original persisted audit baseline.
        var plan=prepare(projectId,loaded,actor);
        String key="DPPMS-LEASE-PRODUCT:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request)));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:LEASE_PRODUCT",0L,key),digest,CorrectionReport.class,()->{
            var result=importer.apply(plan,actor);
            Map<String,NativeEntityMigrationRow> mapped=new LinkedHashMap<>();
            var targets=new ArrayList<CorrectionTarget>();
            for(var row:result.rows()) {
                mapped.put("lease-products:"+row.sourceKey(),row);
                targets.add(new CorrectionTarget(row.sourceKey(),row.targets()));
            }
            evidence.reconcileAssociatedList(loaded.source(),loaded.snapshot(),"lease-products",loaded.domains().get("lease-products"),mapped,result.baselines());
            return new CorrectionReport(request.sourceId(),request.snapshotId(),"lease-products",targets.size(),targets);
        },report->new SuccessFacts("PROJ_DPPMS_LEASE_PRODUCT_RECONCILE","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)
            throw new IllegalArgumentException("DPPMS_LEASE_PRODUCT_"+execution.decision());
        return new CorrectionResult(execution.decision().name(),execution.response());
    }
    private ProjectLeaseProductImportService.Plan prepare(Long projectId,Loaded loaded,ProjectAccessActor actor) {
        return importer.prepare(projectId,loaded.source().getSourceProjectKey(),loaded.domains().get("project"),loaded.domains().get("lease-products"),actor);
    }
}
