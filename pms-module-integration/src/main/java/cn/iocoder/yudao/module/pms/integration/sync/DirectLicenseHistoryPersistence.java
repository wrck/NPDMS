package cn.iocoder.yudao.module.pms.integration.sync;
import static cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RequiredArgsConstructor
final class DirectLicenseHistoryPersistence {
    static final String SOURCE_TABLE="pm_project_license_info_from_license";
    private final javax.sql.DataSource dataSource;
    private final DirectLicenseHistoryMapper records;
    private final DirectLicenseHistoryCipher cipher;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private final PlatformCommandExecutionApi commands;
    private final PlatformMigrationEvidenceApi evidence;
    public record Saved(String sourceKey,Long id,String checksum,String fieldsJson,List<String> pendingAssociations) {}
    record Preview(String sourceKey,Long targetId,String action,List<String> pendingAssociations,List<String> sourceIssues) {}
    private record Entry(License source,String checksum,Saved existing,Long deviceId,List<String> pending,List<String> sourceIssues) {}

    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Preview> preview(Command command) {
        return prepare(command).stream().map(e->new Preview(e.source().sourceKey(),
            e.existing()==null?null:e.existing().id(),e.existing()==null?"CREATE":"UNCHANGED",e.pending(),e.sourceIssues())).toList();
    }

    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Target> apply(Command command) {
        var entries=prepare(command);
        if(entries.isEmpty()) return List.of();
        var saved=new ArrayList<Saved>();
        for(var entry:entries) {
            String key="DPPMS-LICENSE:"+entry.source().sourceKey();
            var result=commands.execute(new IdempotencyScope(command.tenantId(),"AST:DPPMS:LICENSE_INITIAL",0L,key),
                entry.checksum(),Saved.class,()->create(command,entry),
                row->new SuccessFacts("AST_DPPMS_LICENSE_INITIAL","DeviceLicenseRecord",row.id().toString(),key,
                    JsonUtils.toJsonString(Map.of("operatorUserId",command.actorId(),"sourceKey",row.sourceKey())),null,null));
            if(result.decision()==Decision.CONFLICT || result.decision()==Decision.IN_PROGRESS) fail("SOURCE_OR_CONCURRENT_CONFLICT");
            var row=result.response();
            if(row==null || !Objects.equals(row.sourceKey(),entry.source().sourceKey()) || !Objects.equals(row.checksum(),entry.checksum()))
                fail("RECEIPT_INVALID");
            verifyCurrent(command.tenantId(),row,entry.source().envelope().codeMac());
            saved.add(row);
        }
        var newlyBound=new ArrayList<Saved>();
        for(int index=0;index<entries.size();index++) if(entries.get(index).existing()==null)newlyBound.add(saved.get(index));
        if(!newlyBound.isEmpty())appendBindings(command,newlyBound);
        return saved.stream().map(row->new Target(row.sourceKey(),row.id())).toList();
    }

    private List<Entry> prepare(Command command) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource))
            throw new IllegalStateException("DPPMS_LICENSE_TRANSACTION_REQUIRED");
        if(command==null || command.snapshotId()==null || command.snapshotId()<=0 || command.extractedAt()==null
            || command.licenses()==null || command.licenses().size()>200) fail("COMMAND_INVALID");
        authorize(command.tenantId(),command.actorId(),command.projectId());
        var checksums=new LinkedHashMap<String,String>();
        var emptyCodes=new HashSet<String>();
        for(var row:command.licenses()) {
            if(row==null || row.sourceKey()==null || !row.sourceKey().matches("[1-9][0-9]*") || checksums.containsKey(row.sourceKey())
                || row.envelope()==null || !row.sourceKey().equals(row.envelope().sourceKey())
                || row.contractId()!=null && row.contractId()<=0) fail("SOURCE_INVALID");
            length(row.deviceSn(),255);length(row.productModel(),256);length(row.contractNo(),32);
            length(row.contractType(),16);length(row.productCode(),255);length(row.licenseStatus(),255);
            if(cipher.verifyContent(row.envelope(),command.tenantId()).empty())emptyCodes.add(row.sourceKey());
            checksums.put(row.sourceKey(),DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(
                row.sourceKey(),row.deviceSn(),row.productModel(),row.contractNo(),row.contractType(),
                row.productCode(),row.licenseStatus(),row.envelope().codeMac()))));
        }
        if(checksums.isEmpty()) return List.of();
        var preview=evidence.preview(new MigrationPreviewCommand(command.tenantId(),"DPPMS",SOURCE_TABLE,
            checksums.entrySet().stream().map(e->new MigrationPreviewRecordRequest(sourcePk(e.getKey()),e.getValue())).toList()));
        Map<String,MigrationPreviewItem> items=new HashMap<>();
        for(var item:preview.items()) if(items.put(item.sourcePk(),item)!=null) fail("BINDING_INVALID");
        Map<Long,Saved> prior=new HashMap<>();
        for(var batchId:preview.items().stream().map(MigrationPreviewItem::latestBatchId).filter(Objects::nonNull).distinct().toList()) {
            Long after=null;
            do {
                var page=evidence.pageSourceRecords(new MigrationSourceRecordPageQuery(command.tenantId(),batchId,after,500));
                for(var fact:page.records()) {
                    if(fact.sourcePk().startsWith("DPPMS-LICENSE:"))
                        prior.put(fact.sourceRecordId(),JsonUtils.parseObject(fact.sourcePayloadJson(),Saved.class));
                }
                after=page.nextAfterSourceRecordId();
            } while(after!=null);
        }
        var result=new ArrayList<Entry>();
        for(var source:command.licenses()) {
            var item=items.get(sourcePk(source.sourceKey()));Saved existing=null;
            if(item==null) fail("BINDING_INVALID");
            if(item.classification()!=MigrationPreviewClassification.NO_EXISTING_SOURCE) {
                if(item.classification()!=MigrationPreviewClassification.MAPPED || item.targets().size()!=1) fail("SOURCE_OR_BINDING_CONFLICT");
                var target=item.targets().getFirst();existing=prior.get(item.latestSourceRecordId());
                if(existing==null || !"MAPPED".equals(target.resultType()) || !"AST".equals(target.targetContext())
                    || !"DeviceLicenseRecord".equals(target.targetObjectType()) || !"ast_device_license_record".equals(target.targetTable())
                    || !"LICENSE_HISTORY".equals(target.targetRole()) || !Objects.equals(target.targetSequence(),0)
                    || !Objects.equals(target.targetId(),existing.id()) || !Objects.equals(existing.sourceKey(),source.sourceKey())
                    || !Objects.equals(existing.checksum(),checksums.get(source.sourceKey()))) fail("BINDING_INVALID");
                verifyCurrent(command.tenantId(),existing,source.envelope().codeMac());
            } else if(item.latestSourceRecordId()!=null || !item.targets().isEmpty()) fail("BINDING_INVALID");
            var device=source.deviceSn()==null || source.deviceSn().isBlank()?null:records.selectDevice(new DirectLicenseHistoryMapper.DeviceIdentity(command.tenantId(),source.deviceSn()));
            if(device!=null && !Objects.equals(device.getTenantId(),command.tenantId())) fail("DEVICE_TENANT_INVALID");
            Long deviceId=device==null || Boolean.TRUE.equals(device.getDeleted()) || device.getSn()==null
                || !source.deviceSn().equalsIgnoreCase(device.getSn())?null:device.getId();
            var pending=new ArrayList<String>();
            if(deviceId==null)pending.add("DEVICE_NOT_MATCHED");
            if(source.contractId()==null)pending.add("CONTRACT_NOT_UNIQUELY_MATCHED");
            result.add(new Entry(source,checksums.get(source.sourceKey()),existing,deviceId,
                existing==null?List.copyOf(pending):existing.pendingAssociations(),
                emptyCodes.contains(source.sourceKey())?List.of("LICENSE_CODE_EMPTY"):List.of()));
        }
        return List.copyOf(result);
    }

    private Saved create(Command command,Entry entry) {
        var source=entry.source();var row=new DirectLicenseHistoryRow();
        row.setId(IdWorker.getId());row.setTenantId(command.tenantId());row.setDeviceId(entry.deviceId());
        row.setDeviceSn(source.deviceSn());row.setProductModel(source.productModel());row.setContractId(source.contractId());
        row.setContractNo(source.contractNo());row.setContractType(source.contractType());row.setProductCode(source.productCode());
        row.setLicenseStatus(source.licenseStatus());row.setLicenseCodeCiphertext(cipher.seal(source.envelope(),command.tenantId()));
        var now=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);String actor=command.actorId().toString();
        row.setCreator(actor);row.setUpdater(actor);row.setCreateTime(now);row.setUpdateTime(now);row.setDeleted(false);
        if(records.insert(row)!=1)fail("INSERT_FAILED");
        var persisted=records.selectRecordForUpdate(new DirectLicenseHistoryMapper.RecordQuery(command.tenantId(),row.getId()));
        if(persisted==null || !fields(row).equals(fields(persisted)))fail("INSERT_FIELDS_CHANGED");
        return new Saved(source.sourceKey(),row.getId(),entry.checksum(),fields(persisted),entry.pending());
    }
    private void verifyCurrent(Long tenantId,Saved saved,String mac) {
        var row=records.selectRecordForUpdate(new DirectLicenseHistoryMapper.RecordQuery(tenantId,saved.id()));
        if(row==null || Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(),tenantId)
            || !fields(row).equals(saved.fieldsJson()) || !Objects.equals(mac,cipher.verifyStored(row.getLicenseCodeCiphertext(),tenantId)))
            fail("LATER_TARGET_CHANGE");
    }
    static String fields(DirectLicenseHistoryRow row) {
        var mapper=JsonUtils.getObjectMapper().rebuild().changeDefaultPropertyInclusion(ignored ->
            com.fasterxml.jackson.annotation.JsonInclude.Value.construct(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS,
                com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)).build();
        var tree=(tools.jackson.databind.node.ObjectNode)mapper.valueToTree(row);
        tree.put("licenseCodeCiphertext",row.getLicenseCodeCiphertext());return tree.toString();
    }

    private void appendBindings(Command command,List<Saved> rows) {
        String partition=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(command.snapshotId(),rows.stream().map(Saved::sourceKey).toList())));
        String key="DPPMS-LICENSE-BIND:"+partition;
        String purpose="LICENSE_INITIAL_"+partition.substring(0,40);
        String sha=DigestUtil.sha256Hex(JsonUtils.toJsonString(rows.stream().map(r->List.of(r.sourceKey(),r.checksum())).toList()));
        var batch=evidence.createImportBatch(new CreateImportBatchCommand(command.tenantId(),"AST",purpose,key,"DPPMS",
            SOURCE_TABLE,"LICENSE_INITIAL_V1",rows.size(),sha,command.extractedAt(),null,null,key+":CREATE",key));
        var facts=evidence.appendSourceRecords(new AppendMigrationSourceRecordsCommand(rows.stream().map(row ->
            new AppendMigrationSourceRecordCommand(command.tenantId(),batch.batchId(),"DPPMS",SOURCE_TABLE,sourcePk(row.sourceKey()),
                row.sourceKey(),JsonUtils.toJsonString(row),row.checksum(),command.extractedAt(),key)).toList()));
        evidence.markStagedReady(new MarkStagedReadyCommand(command.tenantId(),batch.batchId(),batch.version(),ImportStagingDecision.READY,
            (long)rows.size(),"LICENSE_INITIAL_V1",sha,null,key+":READY",key));
        var claim=evidence.claimStagedBatch(new ClaimStagedBatchCommand(command.tenantId(),"AST",purpose,List.of("DPPMS"),List.of(SOURCE_TABLE),key));
        if(!claim.claimed() || !Objects.equals(claim.batch().batchId(),batch.batchId()))fail("BATCH_CLAIM_FAILED");
        Map<String,Saved> byKey=new HashMap<>();rows.forEach(row->byKey.put(sourcePk(row.sourceKey()),row));
        evidence.appendExternalMappings(new AppendExternalMappingsCommand(facts.stream().map(fact -> {
            var row=byKey.get(fact.sourcePk());
            return new AppendExternalMappingCommand(command.tenantId(),batch.batchId(),fact.sourceRecordId(),SourceReconciliationType.MAPPED,
                List.of(new ExternalTargetMapping("AST","DeviceLicenseRecord","ast_device_license_record",row.id(),"LICENSE_HISTORY",0)),
                key+":ROW:"+fact.sourceRecordId(),key);
        }).toList(),key+":MAP",key));
        evidence.completeReconciliation(new CompleteReconciliationCommand(command.tenantId(),batch.batchId(),claim.batch().version(),
            rows.size(),rows.size(),0,0,"LICENSE_INITIAL_V1",key+":COMPLETE",key));
    }
    private void authorize(Long tenant,Long actor,Long project) {
        if(tenant==null || !Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()) || actor==null || actor<=0
                || project!=null || !permissions.hasAnyPermissions(actor,"pms:integration:execute"))fail("FORBIDDEN");
        users.validateUser(actor);
    }
    private static String sourcePk(String key) {return "DPPMS-LICENSE:"+key;}
    private static void length(String value,int max) {if(value!=null && value.codePointCount(0,value.length())>max)fail("FIELD_TOO_LONG");}
    private static void fail(String reason) {throw new IllegalArgumentException("DPPMS_LICENSE_"+reason);}
}
