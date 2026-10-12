package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.value;

/** Derives target IDs from immutable source and successful bindings; browser input never chooses targets. */
@Service
@RequiredArgsConstructor
public class ProjectShipmentReferenceCorrectionService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final DppmsCommerceEntityResolveApi commerce;
    private final DppmsShipmentReferenceCorrectionApi owner;
    private final PlatformCommandExecutionApi commands;

    public record PreviewRequest(@NotNull @Valid ProjectLegacyEntityMigrationService.Request source,
                                 @NotNull @Size(min=1,max=2000) List<@NotBlank String> sourceKeys) {}
    public record CorrectionRequest(@NotNull @Valid PreviewRequest source,
                                    @NotNull @Size(min=1,max=2000) List<Baseline> baselines) {}
    public record Report(Long sourceId,Long snapshotId,int sourceCount,List<Baseline> corrected) {}
    public record Result(String decision,Report report) {}

    @Transactional(rollbackFor=Exception.class)
    public List<Baseline> preview(Long projectId,PreviewRequest request,ProjectAccessActor actor) {
        return owner.preview(prepare(projectId,request,actor));
    }

    @Transactional(rollbackFor=Exception.class)
    public Result correct(Long projectId,CorrectionRequest request,ProjectAccessActor actor) {
        if(request==null || request.source()==null) fail("REQUEST_INVALID");
        var command=prepare(projectId,request.source(),actor);
        if(request.baselines()==null || request.baselines().stream().anyMatch(Objects::isNull)
                || !command.references().equals(request.baselines().stream().map(Baseline::reference).toList()))
            fail("SUCCESSFUL_BINDINGS_CHANGED");
        String key="DPPMS-SHIPMENT-REFERENCES:"+DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(
                projectId,request.source().source().sourceId(),request.source().source().snapshotId(),request.source().sourceKeys(),"V1")));
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request)));
        var result=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:SHIPMENT_REFERENCES",0L,key),
                digest,Report.class,()->{
                    owner.correct(command,request.baselines());
                    return new Report(request.source().source().sourceId(),request.source().source().snapshotId(),
                            command.references().size(),List.copyOf(request.baselines()));
                },report->new SuccessFacts("PROJ_DPPMS_SHIPMENT_REFERENCES_CORRECT","Project",projectId.toString(),key,
                        JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS) fail(result.decision().name());
        if(result.decision()==Decision.REPLAY_COMPLETED) owner.verify(command,request.baselines());
        return new Result(result.decision().name(),result.response());
    }

    Command prepare(Long projectId,PreviewRequest request,ProjectAccessActor actor) {
        if(request==null || request.source()==null || request.sourceKeys()==null || request.sourceKeys().isEmpty()
                || request.sourceKeys().size()>2000 || request.sourceKeys().stream().anyMatch(key->key==null || key.isBlank())
                || new HashSet<>(request.sourceKeys()).size()!=request.sourceKeys().size()) fail("SOURCE_KEYS_INVALID");
        var loaded=sources.loadCorrectionSource(projectId,request.source(),actor);
        var rows=loaded.domains();
        var selected=rows.get("shipment-facts").stream().filter(row->request.sourceKeys().contains(row.getSourceKey())).toList();
        if(selected.size()!=request.sourceKeys().size()) fail("SOURCE_KEY_NOT_FOUND");
        var shipmentStatuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),loaded.snapshot().getId(),selected);
        var lineRows=rows.get("order-lines");
        var lineStatuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),loaded.snapshot().getId(),lineRows);
        var references=new ArrayList<Reference>();var verifiedLines=new LinkedHashMap<String,DppmsCommerceEntityResolveApi.ShipmentLine>();
        for(String key:request.sourceKeys()) {
            var row=selected.stream().filter(r->Objects.equals(r.getSourceKey(),key)).findFirst().orElseThrow();
            String originalLine=value(row,"lineNum"),orderNo=value(row,"orderNumber"),sn=value(row,"barcode");
            if(originalLine==null || originalLine.isBlank() || orderNo==null || orderNo.isBlank() || sn==null || sn.isBlank())
                fail("SHIPMENT_SOURCE_INVALID");
            var matching=lineRows.stream().filter(line->Objects.equals(value(line,"orderNumber"),orderNo)
                    && Objects.equals(inventoryTransaction(line),originalLine)).toList();
            if(matching.size()!=1) fail("INVENTORY_TRANSACTION_NOT_UNIQUE");
            var line=matching.getFirst();
            Long lineId=target(lineStatuses.get(line.getSourceKey()),"COM","SalesOrderLine","com_sales_order_line","BUSINESS_ENTITY");
            Long shipmentId=target(shipmentStatuses.get(key),"AST","DeviceShipment","ast_device_shipment","SHIPMENT");
            Long deviceId=target(shipmentStatuses.get(key),"AST","Device","ast_device","DEVICE");
            references.add(new Reference(key,shipmentId,deviceId,lineId,sn,orderNo,originalLine));
            verifiedLines.putIfAbsent(line.getSourceKey(),new DppmsCommerceEntityResolveApi.ShipmentLine(line.getSourceKey(),lineId,
                    value(line,"source"),value(line,"compCode"),orderNo,value(line,"lineNum"),originalLine));
        }
        var identities=new DppmsCommerceEntityResolveApi.Command(actor.tenantId(),actor.actorId(),projectId,loaded.source().getSourceContractNo(),
                rows.get("orders").stream().map(row->new DppmsCommerceEntityResolveApi.Order(row.getSourceKey(),value(row,"source"),
                        value(row,"compCode"),value(row,"orderType"),value(row,"orderNumber"))).toList(),
                lineRows.stream().map(row->new DppmsCommerceEntityResolveApi.Line(row.getSourceKey(),value(row,"source"),value(row,"compCode"),
                        value(row,"lineType"),value(row,"orderNumber"),value(row,"lineNum"))).toList(),List.of(),List.of(),null);
        var expected=List.copyOf(verifiedLines.values());
        if(!expected.equals(commerce.lockShipmentLines(identities,expected))) fail("FORMAL_LINE_OWNER_DISAGREED");
        return new Command(actor.tenantId(),actor.actorId(),projectId,List.copyOf(references));
    }
    private static String inventoryTransaction(ProjectLegacyRecordDO row) {
        String json=value(row,"customInfo");
        return json==null || json.isBlank()?null:JsonUtils.parseTree(json).path("inventTransId").asText(null);
    }
    private static Long target(ProjectLegacyNativeEvidenceService.RowStatus status,String context,String type,String table,String role) {
        if(status==null || !"MAPPED".equals(status.classification())) fail("SUCCESSFUL_RECEIPT_REQUIRED");
        var matches=status.targets().stream().filter(t->context.equals(t.targetContext()) && type.equals(t.targetObjectType())
                && table.equals(t.targetTable()) && role.equals(t.targetRole()) && t.targetSequence()==0).toList();
        if(matches.size()!=1 || matches.getFirst().targetId()==null) fail("EXACT_BINDING_REQUIRED");
        return matches.getFirst().targetId();
    }
    private static void fail(String reason) {throw new IllegalArgumentException("DPPMS_SHIPMENT_REFERENCE_"+reason);}
}
