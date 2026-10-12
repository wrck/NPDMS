package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Promote the complete approved product baseline through the formal COM scope Owner. */
@Service
@RequiredArgsConstructor
public class ProjectProductScopeReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectProductScopeImportService importer;
    private final DppmsProjectProductScopeApi scopes;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    @Transactional(rollbackFor=Exception.class)
    public List<DppmsProjectProductScopeApi.Result> preview(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        return scopes.preview(importer.command(projectId,loaded.source().getSourceProjectKey(),loaded.domains().get("products"),actor,true));
    }
    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult reconcile(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);var rows=loaded.domains().get("products");
        var command=importer.command(projectId,loaded.source().getSourceProjectKey(),loaded.domains().get("products"),actor,true);
        Map<String,Long> baselineIds=new HashMap<>();command.entries().forEach(e->baselineIds.put(e.sourceKey(),e.baseline().id()));
        var applied=scopes.apply(command);
        if(applied.size()!=rows.size())throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_COVERAGE_INVALID");
        Map<String,NativeEntityMigrationRow> outcomes=new LinkedHashMap<>();var targets=new ArrayList<CorrectionTarget>();
        Set<String> expected=new HashSet<>();rows.forEach(r->expected.add(r.getSourceKey()));Set<Long> ids=new HashSet<>();
        for(var result:applied) {
            if(!expected.remove(result.sourceKey()) || result.targetId()==null || !Objects.equals(result.targetId(),baselineIds.get(result.sourceKey())) || !ids.add(result.targetId())
                    || !Set.of("CREATED","UPDATED","UNCHANGED").contains(result.action()))throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_OWNER_INVALID");
            var target=new ExternalTargetMapping("COM","DeliveryScope","com_delivery_scope",result.targetId(),"BUSINESS_ENTITY",0);
            outcomes.put("products:"+result.sourceKey(),NativeEntityMigrationRow.mapped("products",result.sourceKey(),target));
            targets.add(new CorrectionTarget(result.sourceKey(),List.of(target)));
        }
        if(!expected.isEmpty())throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_COVERAGE_INVALID");
        Map<String,DppmsProjectProductScopeApi.Baseline> baselines=new HashMap<>();command.entries().forEach(e->baselines.put(e.sourceKey(),e.baseline()));
        String key="DPPMS-PRODUCT-SCOPE:"+request.sourceId()+":"+request.snapshotId()+":V4_PRODUCT_DETAILS";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request,targets)));
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:PRODUCT_SCOPE",0L,key),digest,CorrectionReport.class,()->{
            evidence.reconcile(loaded.source(),loaded.snapshot(),"products",rows,outcomes,"PRODUCT_SCOPE_V4",baselines);
            return new CorrectionReport(request.sourceId(),request.snapshotId(),"products",rows.size(),targets);
        },report->new SuccessFacts("PROJ_DPPMS_PRODUCT_SCOPE_RECONCILE","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report,"ownerResults",applied)),null,null));
        if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS)throw new IllegalArgumentException("DPPMS_PRODUCT_SCOPE_"+result.decision());
        return new CorrectionResult(result.decision().name(),result.response());
    }
}
