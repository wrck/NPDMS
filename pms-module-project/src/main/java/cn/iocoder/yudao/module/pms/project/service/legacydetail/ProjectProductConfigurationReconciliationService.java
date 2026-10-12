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
public class ProjectProductConfigurationReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectProductConfigurationImportService importer;
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
        String key="DPPMS-PRODUCT-CONFIGURATION:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request)));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:PRODUCT_CONFIGURATION",0L,key),digest,CorrectionReport.class,()->{
            var result=importer.apply(plan,actor);
            Map<String,NativeEntityMigrationRow> mapped=new LinkedHashMap<>();
            var targets=new ArrayList<CorrectionTarget>();
            for(var row:result.rows()) {
                mapped.put("configured-products:"+row.sourceKey(),row);
                targets.add(new CorrectionTarget(row.sourceKey(),row.targets()));
            }
            evidence.reconcileAssociatedList(loaded.source(),loaded.snapshot(),"configured-products",loaded.domains().get("configured-products"),mapped,result.baselines());
            return new CorrectionReport(request.sourceId(),request.snapshotId(),"configured-products",targets.size(),targets);
        },report->new SuccessFacts("PROJ_DPPMS_PRODUCT_CONFIGURATION_RECONCILE","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)
            throw new IllegalArgumentException("DPPMS_PRODUCT_CONFIGURATION_"+execution.decision());
        return new CorrectionResult(execution.decision().name(),execution.response());
    }
    private ProjectProductConfigurationImportService.Plan prepare(Long projectId,Loaded loaded,ProjectAccessActor actor) {
        return importer.prepare(projectId,loaded.source().getSourceProjectKey(),loaded.domains().get("project"),loaded.domains().get("configured-products"),actor);
    }
}
