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
public class ProjectRealProductReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectRealProductImportService importer;
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
        String key="DPPMS-REAL-PRODUCT:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request)));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:REAL_PRODUCT",0L,key),digest,CorrectionReport.class,()->{
            var result=importer.apply(plan,actor);
            Map<String,NativeEntityMigrationRow> mapped=new LinkedHashMap<>();
            var targets=new ArrayList<CorrectionTarget>();
            for(var row:result.rows()) {
                mapped.put("real-products:"+row.sourceKey(),row);
                targets.add(new CorrectionTarget(row.sourceKey(),row.targets()));
            }
            evidence.reconcileRealProducts(loaded.source(),loaded.snapshot(),loaded.domains().get("real-products"),mapped,result.baselines());
            var upstream=upstreamOutcomes(plan,loaded.domains().get("real-product-upstream"),result);
            upstream.forEach(row->mapped.put("real-product-upstream:"+row.sourceKey(),row));
            evidence.reconcile(loaded.source(),loaded.snapshot(),"real-product-upstream",loaded.domains().get("real-product-upstream"),mapped);
            return new CorrectionReport(request.sourceId(),request.snapshotId(),"real-products",targets.size(),targets);
        },report->new SuccessFacts("PROJ_DPPMS_REAL_PRODUCT_RECONCILE","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)
            throw new IllegalArgumentException("DPPMS_REAL_PRODUCT_"+execution.decision());
        return new CorrectionResult(execution.decision().name(),execution.response());
    }
    private ProjectRealProductImportService.Plan prepare(Long projectId,Loaded loaded,ProjectAccessActor actor) {
        return importer.prepare(projectId,loaded.source().getSourceProjectKey(),loaded.domains().get("real-products"),
                loaded.domains().get("real-product-upstream"),actor);
    }
    static List<NativeEntityMigrationRow> upstreamOutcomes(ProjectRealProductImportService.Plan plan,
            List<cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO> upstream,
            ProjectRealProductImportService.Result result) {
        var output=new ArrayList<NativeEntityMigrationRow>();
        for(var row:upstream) {
            var targets=new ArrayList<ExternalTargetMapping>();
            for(var entry:plan.entries()) {
                var p=entry.incoming();
                var values=Arrays.asList(p.getProjectCode(),p.getExecutionNo(),p.getProductCategoryName(),p.getProductName(),
                        p.getProductSubCode(),p.getProductSubModel(),p.getProductSubName(),p.getQuantity()==null?null:p.getQuantity().stripTrailingZeros().toPlainString(),p.getMemo());
                if(!values.equals(ProjectRealProductImportService.SHARED_COLUMNS.stream().map(field->value(row,field)).toList()))continue;
                var mapped=result.rows().stream().filter(r->r.sourceKey().equals(entry.sourceKey())).findFirst().orElseThrow();
                var target=mapped.targets().getFirst();
                targets.add(new ExternalTargetMapping(target.targetContext(),target.targetObjectType(),target.targetTable(),target.targetId(),target.targetRole(),targets.size()));
            }
            if(targets.isEmpty())throw new IllegalArgumentException("DPPMS_REAL_PRODUCT_UPSTREAM_UNRELATED");
            output.add(new NativeEntityMigrationRow("real-product-upstream",row.getSourceKey(),targets,null));
        }
        return List.copyOf(output);
    }
}
