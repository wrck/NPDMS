package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Correct only approved execution fields through COM, with separate immutable migration receipts. */
@Service @RequiredArgsConstructor
public class ProjectExecutionFieldReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final DppmsCommerceDetailImportApi commerce;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult reconcile(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        var rows=loaded.domains().get("executions");var contracts=loaded.domains().get("contracts");
        if(rows.isEmpty() || contracts.size()!=1 || !Objects.equals(value(contracts.getFirst(),"contractNo"),loaded.source().getSourceContractNo()))
            throw new IllegalArgumentException("DPPMS_EXECUTION_CORRECTION_SOURCE_INVALID");
        var identity=new DppmsCommerceEntityResolveApi.Command(actor.tenantId(),actor.actorId(),projectId,loaded.source().getSourceContractNo(),
                List.of(),List.of(),rows.stream().map(r->new DppmsCommerceEntityResolveApi.Execution(r.getSourceKey(),value(r,"dataSource"),value(r,"orderExecNumber"))).toList(),
                List.of(),contracts.getFirst().getSourceKey());
        var outcomes=commerce.importDetails(new DppmsCommerceDetailImportApi.Command(identity,List.of(),List.of(),List.of(),List.of(),rows.stream().map(ProjectExecutionFieldReconciliationService::execution).toList()));
        var mapped=new LinkedHashMap<String,NativeEntityMigrationRow>();
        for(var outcome:outcomes) {
            if("contracts".equals(outcome.domain()))continue;
            if(!"executions".equals(outcome.domain()) || mapped.putIfAbsent("executions:"+outcome.sourceKey(),outcome)!=null)
                throw new IllegalArgumentException("DPPMS_EXECUTION_CORRECTION_COVERAGE_INVALID");
        }
        var targets=new ArrayList<CorrectionTarget>();Set<String> expected=new HashSet<>();Set<Long> ids=new HashSet<>();
        for(var row:rows) {
            String key="executions:"+row.getSourceKey();var outcome=mapped.get(key);
            if(!expected.add(key) || outcome==null || outcome.reasonCode()!=null || outcome.targets().size()!=1)
                throw new IllegalArgumentException("DPPMS_EXECUTION_CORRECTION_OWNER_BLOCKED:"+key+":"+(outcome==null?"MISSING":outcome.reasonCode()));
            var target=outcome.targets().getFirst();
            if(!"COM".equals(target.targetContext()) || !"com_crm_execution_order".equals(target.targetTable())
                    || target.targetId()==null || target.targetId()<=0 || !ids.add(target.targetId()))
                throw new IllegalArgumentException("DPPMS_EXECUTION_CORRECTION_TARGET_INVALID");
            targets.add(new CorrectionTarget(key,outcome.targets()));
        }
        if(!expected.equals(mapped.keySet()))throw new IllegalArgumentException("DPPMS_EXECUTION_CORRECTION_COVERAGE_INVALID");
        String key="DPPMS-EXECUTION-FIELDS-CORRECTION:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request,targets)));
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:EXECUTION_FIELDS_CORRECTION",0L,key),digest,CorrectionReport.class,()->{
            evidence.reconcile(loaded.source(),loaded.snapshot(),"executions",rows,mapped,"EXECUTION_FIELDS_V1");
            return new CorrectionReport(request.sourceId(),request.snapshotId(),"executions",rows.size(),targets);
        },report->new SuccessFacts("PROJ_DPPMS_EXECUTION_FIELDS_RECONCILE","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS)throw new IllegalArgumentException("DPPMS_EXECUTION_CORRECTION_"+result.decision());
        return new CorrectionResult(result.decision().name(),result.response());
    }
    static DppmsCommerceDetailImportApi.Execution execution(ProjectLegacyRecordDO r) {
        var raw=JsonUtils.parseObject(r.getPayloadJson(),new TypeReference<Map<String,String>>(){});
        if(raw.size()!=32 || !raw.keySet().containsAll(ORIGINAL_COLUMNS) || !Objects.equals(raw.get("id"),r.getSourceKey()))
            throw new IllegalArgumentException("DPPMS_EXECUTION_CORRECTION_ORIGINAL_FIELDS_INCOMPLETE");
        return new DppmsCommerceDetailImportApi.Execution(r.getSourceKey(),value(r,"orderExecNumber"),value(r,"dataSource"),value(r,"projectCode"),value(r,"projectName"),
                value(r,"salesManCode"),value(r,"salesManName"),value(r,"marketCode"),value(r,"marketName"),value(r,"systemId"),value(r,"systemName"),
                value(r,"expendId"),value(r,"expendName"),value(r,"industryId"),value(r,"industryName"),value(r,"officeCode"),value(r,"officeName"),
                value(r,"serviceTypeName"),value(r,"channelName"),value(r,"engineeFee"),value(r,"objId"),value(r,"applyType"),value(r,"corporationCode"),
                value(r,"customerProjectName"),value(r,"finalCustomerName"),value(r,"agentName"),value(r,"majorProjectLevel"),decimal(value(r,"projectMoney")),
                time(value(r,"submitTime")),time(value(r,"predBidDate")),value(r,"linkmanName"),value(r,"linkmanTel"));
    }
    private static String value(ProjectLegacyRecordDO row,String key) {return JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){}).get(key);}
    static final List<String> ORIGINAL_COLUMNS=List.of("id","orderExecNumber","dataSource","projectCode","projectName","salesManCode","salesManName",
            "marketCode","marketName","systemId","systemName","expendId","expendName","industryId","industryName","officeCode","officeName",
            "serviceTypeName","channelName","engineeFee","objId","applyType","corporationCode","customerProjectName","finalCustomerName","agentName",
            "majorProjectLevel","projectMoney","submitTime","predBidDate","linkmanName","linkmanTel");
}
