package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Bind formal records independently of the unique factory master, preserving every source occurrence. */
@Service @RequiredArgsConstructor
public class ProjectFactoryRecordReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectFactoryRecordImportService importer;
    private final DppmsAssetSourceHistoryApi assets;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult reconcile(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        // Replay must still validate current business fields and the exact mapped identities.
        var precheck=importer.command(projectId,loaded,actor);
        if(precheck.factories().stream().anyMatch(f->f.mappedRecordId()!=null)) {
            var mapped=precheck.factories().stream().filter(f->f.mappedRecordId()!=null).toList();
            assets.importSourceHistory(new DppmsAssetSourceHistoryApi.Command(precheck.tenantId(),precheck.actorId(),precheck.projectId(),
                    precheck.sourceProjectKey(),precheck.sourceVersion(),precheck.sourceReadAt(),List.of(),List.of(),mapped));
        }
        String key="DPPMS-FACTORY-RECORD:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request)));
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:FACTORY_RECORD",0L,key),digest,CorrectionReport.class,()->{
            var command=importer.command(projectId,loaded,actor);
            var outcomes=assets.importSourceHistory(command);
            if(outcomes.size()!=command.factories().size())throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_COVERAGE_INVALID");
            Map<String,NativeEntityMigrationRow> mapped=new LinkedHashMap<>();var targets=new ArrayList<CorrectionTarget>();Set<Long> ids=new HashSet<>();
            for(var outcome:outcomes) {
                if(!"factory-versions".equals(outcome.domain()) || outcome.reasonCode()!=null || outcome.targets().size()!=1
                        || mapped.putIfAbsent("factory-versions:"+outcome.sourceKey(),outcome)!=null)
                    throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_OWNER_INVALID");
                var target=outcome.targets().getFirst();
                if(!"AST".equals(target.targetContext()) || !"DeviceFactoryVersionRecord".equals(target.targetObjectType())
                        || !"ast_device_factory_version_record".equals(target.targetTable()) || !"FACTORY_VERSION_SOURCE".equals(target.targetRole())
                        || target.targetSequence()!=0 || target.targetId()==null || target.targetId()<=0 || !ids.add(target.targetId()))
                    throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_OWNER_INVALID");
                targets.add(new CorrectionTarget(outcome.sourceKey(),outcome.targets()));
            }
            var expected=new HashSet<String>();command.factories().forEach(f->expected.add("factory-versions:"+f.sourceKey()));
            if(!expected.equals(mapped.keySet()))throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_COVERAGE_INVALID");
            evidence.reconcile(loaded.source(),loaded.snapshot(),"factory-versions",loaded.domains().get("factory-versions"),mapped,"FACTORY_RECORD_V1");
            return new CorrectionReport(request.sourceId(),request.snapshotId(),"factory-versions",outcomes.size(),targets);
        },report->new SuccessFacts("PROJ_DPPMS_FACTORY_RECORD_RECONCILE","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS)throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_"+result.decision());
        return new CorrectionResult(result.decision().name(),result.response());
    }
}
