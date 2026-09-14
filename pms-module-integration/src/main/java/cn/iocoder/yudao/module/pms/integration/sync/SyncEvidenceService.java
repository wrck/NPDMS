package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SyncEvidenceService {
    private final PlatformMigrationEvidenceApi api;
    public record Evidence(String object,String sourceObject,Long batchId,Map<String,Long> recordIds) {}

    @Transactional(rollbackFor=Exception.class)
    public List<Evidence> stage(Long runId, SyncDefinition d, MysqlSyncReader.Snapshot snapshot) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        String correlation=runId.toString(),purpose="SYNC_"+runId;
        List<Evidence> evidence=new ArrayList<>();
        for(var source:snapshot.objects()) {
            var config=d.sources().stream().filter(s->s.object().equals(source.object())).findFirst().orElseThrow();
            String payload=JsonUtils.toJsonString(source.rows()),digest=sha(payload);
            var batch=api.createImportBatch(new CreateImportBatchCommand(tenant,"INT",purpose,correlation,
                    d.sourceSystem(),source.sourceObject(),"SYNC_V1",source.rows().size(),digest,snapshot.upper(),
                    null,null,purpose+":"+source.object(),correlation));
            Map<String,Long> ids=new LinkedHashMap<>();
            List<AppendMigrationSourceRecordCommand> sourceCommands=new ArrayList<>();
            for(var row:source.rows()) {
                String key=row.get(config.sourceKey()).toString(),json=JsonUtils.toJsonString(row);
                sourceCommands.add(new AppendMigrationSourceRecordCommand(tenant,batch.batchId(),
                        d.sourceSystem(),source.sourceObject(),key,null,json,sha(json),snapshot.upper(),correlation));
            }
            for(int start=0;start<sourceCommands.size();start+=1000) {
                var page=sourceCommands.subList(start,Math.min(start+1000,sourceCommands.size()));
                api.appendSourceRecords(new AppendMigrationSourceRecordsCommand(page))
                        .forEach(record->ids.put(record.sourcePk(),record.sourceRecordId()));
            }
            api.markStagedReady(new MarkStagedReadyCommand(tenant,batch.batchId(),batch.version(),
                    ImportStagingDecision.READY,(long)source.rows().size(),"SYNC_V1",digest,null,
                    "ready:"+batch.batchId(),correlation));
            evidence.add(new Evidence(source.object(),source.sourceObject(),batch.batchId(),ids));
        }
        return evidence;
    }

    /** Called inside the SAME transaction as the target owner and checkpoint. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY,rollbackFor=Exception.class)
    public void complete(Long runId, SyncDefinition d,List<Evidence> evidence,
                         DataSyncAdapter.Descriptor descriptor,List<DataSyncAdapter.Change> changes,boolean failed) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        String correlation=runId.toString();
        Map<String,DataSyncAdapter.Change> byKey=new HashMap<>();
        changes.forEach(c->byKey.put(c.object()+":"+c.sourceKey(),c));
        for(var e:evidence) {
            var claim=api.claimStagedBatch(new ClaimStagedBatchCommand(tenant,"INT","SYNC_"+runId,
                    List.of(d.sourceSystem()),List.of(e.sourceObject()),correlation));
            if(!claim.claimed()||!claim.batch().batchId().equals(e.batchId())) throw new IllegalStateException("迁移证据批次领取失败");
            long mapped=0,issues=0,retained=0;
            List<AppendExternalMappingCommand> mappingCommands=new ArrayList<>();
            var object=descriptor.objects().stream().filter(o->o.name().equals(e.object())).findFirst().orElseThrow();
            for(var pair:e.recordIds().entrySet()) {
                var change=byKey.get(e.object()+":"+pair.getKey());
                if(failed) {
                    api.appendMigrationIssue(new AppendMigrationIssueCommand(tenant,e.batchId(),pair.getValue(),
                            "SYNC_BATCH_FAILED","BATCH_ROLLED_BACK",pair.getKey(),List.of(),null,
                            "issue:"+pair.getValue(),correlation));issues++;
                } else if(change!=null && "ISSUE".equals(change.action())) {
                    api.appendMigrationIssue(new AppendMigrationIssueCommand(tenant,e.batchId(),pair.getValue(),
                            "SYNC_SOURCE_ISSUE", "SOURCE_DATA_INVALID", pair.getKey(),List.of(),
                            JsonUtils.toJsonString(Map.of("reason",change.message())),
                            "issue:"+pair.getValue(),correlation));issues++;
                } else if(change==null||change.targetId()==null||"SKIPPED".equals(change.action())) {
                    mappingCommands.add(new AppendExternalMappingCommand(tenant,e.batchId(),pair.getValue(),
                            SourceReconciliationType.RETAINED,List.of(),"retained:"+pair.getValue(),correlation));retained++;
                } else {
                    mappingCommands.add(new AppendExternalMappingCommand(tenant,e.batchId(),pair.getValue(),
                            SourceReconciliationType.MAPPED,List.of(new ExternalTargetMapping(object.targetContext(),
                            object.targetObjectType(),object.targetTable(),change.targetId(),"PRIMARY",0)),
                            "mapping:"+pair.getValue(),correlation));mapped++;
                }
            }
            for(int start=0;start<mappingCommands.size();start+=1000) {
                var page=mappingCommands.subList(start,Math.min(start+1000,mappingCommands.size()));
                api.appendExternalMappings(new AppendExternalMappingsCommand(page,
                        "mapping-page:"+page.getFirst().sourceRecordId(),correlation));
            }
            api.completeReconciliation(new CompleteReconciliationCommand(tenant,e.batchId(),claim.batch().version(),
                    e.recordIds().size(),mapped,issues,retained,"SYNC_V1","complete:"+e.batchId(),correlation));
        }
    }
    // Required by the pre-existing PLT evidence contract; not used for change detection.
    private static String sha(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
