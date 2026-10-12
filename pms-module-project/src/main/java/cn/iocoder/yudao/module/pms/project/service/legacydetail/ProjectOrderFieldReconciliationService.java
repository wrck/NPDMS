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
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;

/** Reconcile approved order fields through COM; preserve every previous migration receipt. */
@Service
@RequiredArgsConstructor
public class ProjectOrderFieldReconciliationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final DppmsCommerceDetailImportApi commerce;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;

    @Transactional(rollbackFor=Exception.class)
    public CorrectionResult reconcile(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        var rows=loaded.domains();var contracts=rows.get("contracts");
        if(contracts.size()!=1 || !Objects.equals(value(contracts.getFirst(),"contractNo"),loaded.source().getSourceContractNo()))
            throw new IllegalArgumentException("DPPMS_ORDER_CORRECTION_CONTRACT_INVALID");
        var identities=new DppmsCommerceEntityResolveApi.Command(actor.tenantId(),actor.actorId(),projectId,
                loaded.source().getSourceContractNo(),
                rows.get("orders").stream().map(r->new DppmsCommerceEntityResolveApi.Order(r.getSourceKey(),value(r,"source"),
                        value(r,"compCode"),value(r,"orderType"),value(r,"orderNumber"))).toList(),
                rows.get("order-lines").stream().map(r->new DppmsCommerceEntityResolveApi.Line(r.getSourceKey(),value(r,"source"),
                        value(r,"compCode"),value(r,"lineType"),value(r,"orderNumber"),value(r,"lineNum"))).toList(),
                List.of(),List.of(),contracts.getFirst().getSourceKey());
        var incoming=new DppmsCommerceDetailImportApi.Command(identities,
                rows.get("orders").stream().map(r->new DppmsCommerceDetailImportApi.Order(r.getSourceKey(),
                        value(r,"contractNo"),value(r,"orderExecNumber"),time(value(r,"orderCreateTime")),time(value(r,"customerRequireTime")),
                        value(r,"customerCode"),value(r,"customerName"),value(r,"projectName"),value(r,"orderComment"),value(r,"salesType"),
                        value(r,"customInfo"),value(r,"compCode"),value(r,"source"),value(r,"id"),time(value(r,"syncTime")))).toList(),
                rows.get("order-lines").stream().map(r->new DppmsCommerceDetailImportApi.Line(r.getSourceKey(),
                        value(r,"itemCode"),value(r,"itemDesc"),decimal(value(r,"orderQuantity")),decimal(value(r,"openQuantity")),
                        value(r,"bundleCode"),integer(value(r,"warrantyMonth")),value(r,"lineType"),value(r,"profitCenter"),
                        value(r,"realOrderExecNumber"),value(r,"customInfo"),value(r,"compCode"),value(r,"source"),value(r,"id"),time(value(r,"syncTime")))).toList(),
                List.of(),List.of(),List.of());
        var outcomes=commerce.importDetails(incoming);
        var mapped=new LinkedHashMap<String,NativeEntityMigrationRow>();
        for(var outcome:outcomes) {
            if("contracts".equals(outcome.domain()))continue;
            String key=outcome.domain()+":"+outcome.sourceKey();
            if(mapped.putIfAbsent(key,outcome)!=null)throw new IllegalArgumentException("DPPMS_ORDER_CORRECTION_DUPLICATE_RESULT");
        }
        var targets=new ArrayList<CorrectionTarget>();var expected=new HashSet<String>();
        for(String domain:List.of("orders","order-lines"))for(var row:rows.get(domain)) {
            String key=domain+":"+row.getSourceKey();var outcome=mapped.get(key);
            String table="orders".equals(domain)?"com_sales_order":"com_sales_order_line";
            if(!expected.add(key) || outcome==null || outcome.reasonCode()!=null || outcome.targets().size()!=1
                    || !"COM".equals(outcome.targets().getFirst().targetContext())
                    || !table.equals(outcome.targets().getFirst().targetTable())
                    || outcome.targets().getFirst().targetId()==null || outcome.targets().getFirst().targetId()<=0)
                throw new IllegalArgumentException("DPPMS_ORDER_CORRECTION_OWNER_BLOCKED:"+key+":"+(outcome==null?"MISSING":outcome.reasonCode()));
            targets.add(new CorrectionTarget(key,outcome.targets()));
        }
        if(expected.isEmpty() || !expected.equals(mapped.keySet()))throw new IllegalArgumentException("DPPMS_ORDER_CORRECTION_COVERAGE_INVALID");
        String key="DPPMS-ORDER-FIELDS-CORRECTION:"+request.sourceId()+":"+request.snapshotId()+":V1";
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request,targets)));
        // The Owner validates current identities and values even when a completed correction is replayed.
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:ORDER_FIELDS_CORRECTION",0L,key),
                digest,CorrectionReport.class,()->{
                    for(String domain:List.of("orders","order-lines"))if(!rows.get(domain).isEmpty())
                        evidence.reconcile(loaded.source(),loaded.snapshot(),domain,rows.get(domain),mapped,"ORDER_FIELDS_V1");
                    return new CorrectionReport(request.sourceId(),request.snapshotId(),"orders-and-lines",targets.size(),targets);
                },report->new SuccessFacts("PROJ_DPPMS_ORDER_FIELDS_RECONCILE","Project",projectId.toString(),key,
                        JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS)
            throw new IllegalArgumentException("DPPMS_ORDER_CORRECTION_"+result.decision());
        return new CorrectionResult(result.decision().name(),result.response());
    }
    private static String value(ProjectLegacyRecordDO row,String key) {
        return JsonUtils.parseObject(row.getPayloadJson(),new tools.jackson.core.type.TypeReference<Map<String,String>>(){}).get(key);
    }
}
