package cn.iocoder.yudao.module.pms.integration.sync;
import static cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryExtensionApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Preserves all source fields as typed business history without changing any current projection. */
@RequiredArgsConstructor
final class DirectSoftwareHistoryPersistence {
    private final javax.sql.DataSource dataSource;
    private final DirectSoftwareHistoryMapper observations;
    private final DirectSoftwareBatchMapper batches;
    private final DirectDeviceBaseMapper identities;
    private final ProjectDeviceAssignmentGuardApi projectGuard;
    private final SystemUserMigrationApi historicalUsers;
    private final DppmsSoftwareHistoryExtensionApi legacyExtensions;

    
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<NativeEntityMigrationRow> importSourceHistory(Command command) {return executeSourceHistory(command,true).stream().map(SourceOutcome::mapped).toList();}
     @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Preview> previewSourceHistory(Command command) {
        if(command==null || command.factories()==null || !command.factories().isEmpty())throw new IllegalArgumentException("SOFTWARE_PREVIEW_REQUIRES_NO_FACTORY");
        return executeSourceHistory(command,false).stream().map(o->new Preview(o.domain(),o.sourceKey(),o.id())).toList();
    }
    private List<SourceOutcome> executeSourceHistory(Command command,boolean apply) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(dataSource))
            throw new IllegalStateException("DPPMS_SOFTWARE_TRANSACTION_REQUIRED");
        if(command==null || command.factories()==null || !command.factories().isEmpty())throw new IllegalArgumentException("SOFTWARE_REQUIRES_NO_FACTORY");
        requireTenant(command.tenantId());
        if (command.actorId()==null||command.projectId()==null||command.sourceProjectKey()==null
                ||command.sourceReadAt()==null||command.observations()==null||command.batches()==null||command.factories()==null)
            throw new IllegalArgumentException("DPPMS_SOURCE_HISTORY_COMMAND_INVALID");
        var guard=projectGuard.validate(new ProjectDeviceAssignmentGuardQuery(command.tenantId(),command.projectId(),command.actorId()));
        if (guard==null||!guard.assignable()) throw new IllegalArgumentException("DPPMS_SOURCE_HISTORY_PROJECT_FORBIDDEN");
        var result=new ArrayList<SourceOutcome>();
        Map<Long,Long> importedBatchIds=new HashMap<>();
        for (var source:command.batches()) {
            var outcome=importBatch(new BatchWrite(command,source,apply));result.add(outcome);
            if (importedBatchIds.put(source.sourceId(),outcome.id())!=null)
                throw new IllegalArgumentException("DPPMS_BATCH_SOURCE_IDENTITY_DUPLICATE");
        }
        Set<Long> sourceIds=new HashSet<>(), targetIds=new HashSet<>();
        for (var source:command.observations()) {
            requireSourceProject(command,source.sourceProjectId(),source.sourceId());
            if (!sourceIds.add(source.sourceId()) || source.mappedRecordId()!=null && !targetIds.add(source.mappedRecordId()))
                throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_IDENTITY_DUPLICATE");
            var resolved=source.mappedBatchId()==null && source.logId()!=null
                    ? source.withMapping(source.mappedRecordId(),importedBatchIds.get(source.logId()),source.extensionDefinitionId()) : source;
            if(!apply && resolved.mappedBatchId()!=null && resolved.mappedBatchId()<0
                    && !Objects.equals(importedBatchIds.get(resolved.logId()),resolved.mappedBatchId()))
                throw new IllegalArgumentException("DPPMS_SOFTWARE_PREVIEW_PARENT_INVALID");
            result.add(importObservation(new ObservationWrite(command,resolved,false,apply)));
        }
        return List.copyOf(result);
    }
    private record SourceOutcome(String domain,String sourceKey,String type,String table,Long id,String role) {
        NativeEntityMigrationRow mapped(){return NativeEntityMigrationRow.mapped(domain,sourceKey,target(type,table,id,role));}
    }
    private record ObservationWrite(Command command,SoftwareObservation source,boolean approvedExistingId,boolean apply) {}
    private SourceOutcome importObservation(ObservationWrite write) {
        var command=write.command();var source=write.source();boolean approvedExistingId=write.approvedExistingId();
        if (source.sourceCreatedAt()==null || source.extensionDefinitionId()==null
                || source.logId()!=null && source.mappedBatchId()==null || source.logId()==null && source.mappedBatchId()!=null)
            throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_RELATION_OR_DATE_MISSING");
        if (source.customInfo()!=null && !JsonUtils.isJson(source.customInfo()))
            throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_CUSTOM_INFO_INVALID");
        if (source.mappedBatchId()!=null && (write.apply() || source.mappedBatchId()>0) && batches.selectRecordForUpdate(new DirectSoftwareBatchMapper.RecordIdentity(
                command.tenantId(),command.projectId(),source.mappedBatchId()))==null)
            throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_BATCH_NOT_IN_PROJECT");
        var incoming=new DirectSoftwareHistoryRow();incoming.setTenantId(command.tenantId());
        incoming.setProjectId(command.projectId());incoming.setDeviceSn(source.barCode());
        var device=source.barCode()==null ? null : identities.selectIdentityForUpdate(
                new DirectDeviceBaseMapper.DeviceIdentityQuery(command.tenantId(),source.barCode()));
        // Verified historical association is independent of present device ownership.
        if (device!=null && !Boolean.TRUE.equals(device.getDeleted())) incoming.setDeviceId(device.getId());
        incoming.setChangeBatchId(source.mappedBatchId());incoming.setContractNo(source.contractNo());incoming.setProductCode(source.itemCode());
        incoming.setConpVersion(source.conp());incoming.setConpType(source.conpType());incoming.setConpSeries(source.conpSeries());incoming.setConpMark(source.conpMark());
        incoming.setBootVersion(source.boot());incoming.setCpldVersion(source.cpld());incoming.setPcbVersion(source.pcb());
        incoming.setExecuteTime(source.executeTime());incoming.setDataState(source.datastate());incoming.setCustomInfo(source.customInfo());
        incoming.setDataSource("DPPMS");incoming.setRecordedAt(source.sourceCreatedAt());
        incoming.setCreator(softwareActor(command.tenantId(),source.sourceCreatedBy()));incoming.setCreateTime(source.sourceCreatedAt());
        incoming.setUpdater(softwareActor(command.tenantId(),source.sourceUpdatedBy()));
        incoming.setUpdateTime(source.sourceUpdatedAt()==null?source.sourceCreatedAt():source.sourceUpdatedAt());incoming.setDeleted(false);
        if (source.mappedRecordId()==null) {
            incoming.setId(write.apply()?IdWorker.getId():-source.sourceId());
            if (write.apply() && observations.insertHistorical(new DirectSoftwareHistoryMapper.HistoricalWrite(incoming))!=1) throw new IllegalStateException("DPPMS_SOFTWARE_HISTORY_INSERT_FAILED");
        } else {
            if (source.mappedRecordId()<=0) throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_MAPPING_INVALID");
            incoming.setId(source.mappedRecordId());
            var existing=observations.selectForUpdate(new DirectSoftwareHistoryMapper.RecordLock(command.tenantId(),source.mappedRecordId()));
            if (existing==null && approvedExistingId) {
                if (observations.insertHistorical(new DirectSoftwareHistoryMapper.HistoricalWrite(incoming))!=1) throw new IllegalStateException("DPPMS_SOFTWARE_HISTORY_INSERT_FAILED");
            } else if (!sameHistory(incoming,existing)) throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_TARGET_CHANGED");
        }
        var attributes=new LinkedHashMap<String,Object>();
        attributes.put("conp_bak",source.conpBak());attributes.put("conp_change",source.conpChange());
        attributes.put("boot_bak",source.bootBak());attributes.put("boot_change",source.bootChange());
        attributes.put("cpld_bak",source.cpldBak());attributes.put("cpld_change",source.cpldChange());
        attributes.put("pcb_bak",source.pcbBak());attributes.put("pcb_change",source.pcbChange());
        if(write.apply())legacyExtensions.initialize(new DppmsSoftwareHistoryExtensionApi.Initialization(command.tenantId(),command.actorId(),incoming.getId(),source.extensionDefinitionId(),attributes));
        else legacyExtensions.preview(new DppmsSoftwareHistoryExtensionApi.Initialization(
                command.tenantId(),command.actorId(),incoming.getId(),source.extensionDefinitionId(),attributes));
        return new SourceOutcome("software-versions",String.valueOf(source.sourceId()),"SoftwareVersionRecord","ast_device_software_version_record",incoming.getId(),"SOFTWARE_VERSION_RECORD");
    }
    private static boolean sameHistory(DirectSoftwareHistoryRow a,DirectSoftwareHistoryRow b) {
        if (b==null) return false;
        // MySQL JSON canonicalizes object spacing/order. Equality remains structural, including null/empty values.
        if (!Objects.equals(a.getCustomInfo(),b.getCustomInfo())) {
            if (a.getCustomInfo()==null || b.getCustomInfo()==null
                    || !Objects.equals(JsonUtils.parseTree(a.getCustomInfo()),JsonUtils.parseTree(b.getCustomInfo()))) return false;
        }
        var copy=new DirectSoftwareHistoryRow();org.springframework.beans.BeanUtils.copyProperties(b,copy);
        copy.setCustomInfo(a.getCustomInfo());return a.equals(copy);
    }
    private String softwareActor(Long tenant,String original) {
        if (original==null || original.isEmpty()) return original;
        var matches=historicalUsers.getHistoricalIdentitiesByUsername(tenant,Set.of(original));
        if (matches.size()!=1 || !Objects.equals(matches.getFirst().username(),original)
                || matches.getFirst().id()==null || matches.getFirst().id()<=0)
            throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_ACTOR_UNRESOLVED");
        return matches.getFirst().id().toString();
    }
    private record BatchWrite(Command command,SoftwareBatch source,boolean apply) {}
    private NativeEntityMigrationRow importBatch(Command command,SoftwareBatch source) {return importBatch(new BatchWrite(command,source,true)).mapped();}
    private SourceOutcome importBatch(BatchWrite write) {
        var command=write.command();var source=write.source();
        requireSourceProject(command,source.sourceProjectId(),source.sourceId());
        if (source.sourceCreatedAt()==null || source.latest()!=null && !Set.of(0,1).contains(source.latest())
                || source.changeVersion()!=null && source.changeVersion().length()>10
                || source.changeRemark()!=null && source.changeRemark().length()>255)
            throw new IllegalArgumentException("DPPMS_BATCH_VALUES_INVALID");
        String key=String.valueOf(source.sourceId());
        var incoming=new DirectSoftwareBatchRow();
        incoming.setTenantId(command.tenantId());incoming.setProjectId(command.projectId());
        incoming.setChangeVersion(source.changeVersion());incoming.setChangeRemark(source.changeRemark());incoming.setLatest(source.latest());
        incoming.setCreator(batchActor(command.tenantId(),source.sourceCreatedBy()));incoming.setCreateTime(source.sourceCreatedAt());
        incoming.setUpdater(batchActor(command.tenantId(),source.sourceUpdatedBy()));
        incoming.setUpdateTime(source.sourceUpdatedAt()==null?source.sourceCreatedAt():source.sourceUpdatedAt());
        incoming.setDeleted(false);
        DirectSoftwareBatchRow row;
        if (source.mappedRecordId()==null) {
            row=incoming;row.setId(write.apply()?IdWorker.getId():-source.sourceId());
            // Explicit XML preserves original NULL actors rather than framework auto-fill.
            if (write.apply() && batches.insertHistorical(new DirectSoftwareBatchMapper.HistoricalWrite(row))!=1) throw new IllegalStateException("DPPMS_BATCH_INSERT_FAILED");
        } else {
            if (source.mappedRecordId()<=0) throw new IllegalArgumentException("DPPMS_BATCH_MAPPING_INVALID");
            incoming.setId(source.mappedRecordId());
            row=batches.selectRecordForUpdate(new DirectSoftwareBatchMapper.RecordIdentity(command.tenantId(),command.projectId(),source.mappedRecordId()));
            if (!incoming.equals(row)) {
                var expected=JsonUtils.parseTree(JsonUtils.toJsonString(incoming));
                var actual=row==null?null:JsonUtils.parseTree(JsonUtils.toJsonString(row));
                var differences=new ArrayList<String>();
                for (String field:List.of("id","tenantId","projectId","changeVersion","changeRemark","latest","creator","createTime","updater","updateTime","deleted"))
                    if(actual==null || !Objects.equals(expected.get(field),actual.get(field))) differences.add(field);
                throw new IllegalArgumentException("DPPMS_BATCH_TARGET_CHANGED:"+String.join(",",differences));
            }
        }
        return new SourceOutcome("software-changes",key,"DeviceSoftwareChangeBatch","ast_device_software_change_batch",row.getId(),"SOFTWARE_BATCH");
    }
    private String batchActor(Long tenant,String original) {
        if (original==null || original.isEmpty()) return original;
        if (original.length()>64) throw new IllegalArgumentException("DPPMS_BATCH_ACTOR_TOO_LONG");
        var matches=historicalUsers.getHistoricalIdentitiesByUsername(tenant,Set.of(original));
        if (matches.size()>1 || !matches.isEmpty() && (!Objects.equals(matches.getFirst().username(),original)
                || matches.getFirst().id()==null || matches.getFirst().id()<=0))
            throw new IllegalArgumentException("DPPMS_BATCH_ACTOR_IDENTITY_AMBIGUOUS");
        return matches.isEmpty()?original:matches.getFirst().id().toString();
    }
    private static void requireTenant(Long tenantId) {
        if (!Objects.equals(tenantId,TenantContextHolder.getRequiredTenantId())) throw new IllegalArgumentException("DPPMS_SOURCE_HISTORY_TENANT_MISMATCH");
    }
    private static void requireSourceProject(Command command,Long sourceProjectId,Long sourceId) {
        if (sourceId==null||sourceProjectId==null||!command.sourceProjectKey().equals(String.valueOf(sourceProjectId)))
            throw new IllegalArgumentException("DPPMS_SOURCE_HISTORY_SOURCE_IDENTITY_MISMATCH");
    }
    private static ExternalTargetMapping target(String type,String table,Long id,String role) {
        return new ExternalTargetMapping("AST",type,table,id,role,0);
    }
}
