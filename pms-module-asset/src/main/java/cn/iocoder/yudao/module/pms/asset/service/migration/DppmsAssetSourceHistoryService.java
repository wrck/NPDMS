package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMigrationIdentityMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Preserves all source fields as typed business history without changing any current projection. */
@Service
@RequiredArgsConstructor
public class DppmsAssetSourceHistoryService implements DppmsAssetSourceHistoryApi {
    private final DeviceSoftwareVersionRecordMapper observations;
    private final DeviceSoftwareChangeBatchMapper batches;
    private final DeviceFactoryVersionRecordMapper factories;
    private final DeviceMigrationIdentityMapper identities;
    private final ProjectDeviceAssignmentGuardApi projectGuard;
    private final DeviceAccessScopeService scope;
    private final SystemUserMigrationApi historicalUsers;
    private final cn.iocoder.yudao.module.pms.asset.service.version.SoftwareVersionLegacyExtensionService legacyExtensions;
    private final cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi extensions;

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<NativeEntityMigrationRow> importSourceHistory(Command command) {return executeSourceHistory(command,true).stream().map(SourceOutcome::mapped).toList();}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Preview> previewSourceHistory(Command command) {
        if(command==null || command.factories()==null || !command.factories().isEmpty())throw new IllegalArgumentException("SOFTWARE_PREVIEW_REQUIRES_NO_FACTORY");
        return executeSourceHistory(command,false).stream().map(o->new Preview(o.domain(),o.sourceKey(),o.id())).toList();
    }
    private List<SourceOutcome> executeSourceHistory(Command command,boolean apply) {
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
        Set<String> factoryKeys=new HashSet<>();
        Set<Long> factoryIds=new HashSet<>();
        for (var source:command.factories()) {
            if (source.sourceKey()==null||source.sourceKey().isBlank()||source.serialNumber()==null||source.serialNumber().isBlank()
                    ||!factoryKeys.add(source.sourceKey()) || (source.mappedRecordId()!=null && !factoryIds.add(source.mappedRecordId())))
                throw new IllegalArgumentException("DPPMS_FACTORY_SOURCE_IDENTITY_INVALID");
            // Verified historical project association does not require a current device assignment.
            var outcome=importFactory(command,source);var target=outcome.targets().getFirst();
            result.add(new SourceOutcome(outcome.domain(),outcome.sourceKey(),target.targetObjectType(),target.targetTable(),target.targetId(),target.targetRole()));
        }
        return List.copyOf(result);
    }
    NativeEntityMigrationRow importCorrectedObservation(Command command,SoftwareObservation source) {
        return importObservation(command,source,true);
    }
    private record SourceOutcome(String domain,String sourceKey,String type,String table,Long id,String role) {
        NativeEntityMigrationRow mapped(){return NativeEntityMigrationRow.mapped(domain,sourceKey,target(type,table,id,role));}
    }
    private record ObservationWrite(Command command,SoftwareObservation source,boolean approvedExistingId,boolean apply) {}
    private NativeEntityMigrationRow importObservation(Command command,SoftwareObservation source,boolean approvedExistingId) {
        return importObservation(new ObservationWrite(command,source,approvedExistingId,true)).mapped();
    }
    private SourceOutcome importObservation(ObservationWrite write) {
        var command=write.command();var source=write.source();boolean approvedExistingId=write.approvedExistingId();
        if (source.sourceCreatedAt()==null || source.extensionDefinitionId()==null
                || source.logId()!=null && source.mappedBatchId()==null || source.logId()==null && source.mappedBatchId()!=null)
            throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_RELATION_OR_DATE_MISSING");
        if (source.customInfo()!=null && !JsonUtils.isJson(source.customInfo()))
            throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_CUSTOM_INFO_INVALID");
        if (source.mappedBatchId()!=null && (write.apply() || source.mappedBatchId()>0) && batches.selectRecordForUpdate(new DeviceSoftwareBatchIdentityQuery(
                command.tenantId(),command.projectId(),source.mappedBatchId()))==null)
            throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_BATCH_NOT_IN_PROJECT");
        var incoming=new DeviceSoftwareVersionRecordDO();incoming.setTenantId(command.tenantId());
        incoming.setProjectId(command.projectId());incoming.setDeviceSn(source.barCode());
        var device=source.barCode()==null ? null : identities.selectIdentityForUpdate(
                new DeviceMigrationIdentityMapper.DeviceIdentityQuery(command.tenantId(),source.barCode()));
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
            if (write.apply() && observations.insertHistorical(new DeviceSoftwareRecordHistoricalWrite(incoming))!=1) throw new IllegalStateException("DPPMS_SOFTWARE_HISTORY_INSERT_FAILED");
        } else {
            if (source.mappedRecordId()<=0) throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_MAPPING_INVALID");
            incoming.setId(source.mappedRecordId());
            var existing=observations.selectForUpdate(new DeviceSoftwareVersionRecordLockQuery(command.tenantId(),source.mappedRecordId()));
            if (existing==null && approvedExistingId) {
                if (observations.insertHistorical(new DeviceSoftwareRecordHistoricalWrite(incoming))!=1) throw new IllegalStateException("DPPMS_SOFTWARE_HISTORY_INSERT_FAILED");
            } else if (!sameHistory(incoming,existing)) throw new IllegalArgumentException("DPPMS_SOFTWARE_HISTORY_TARGET_CHANGED");
        }
        var attributes=new LinkedHashMap<String,Object>();
        attributes.put("conp_bak",source.conpBak());attributes.put("conp_change",source.conpChange());
        attributes.put("boot_bak",source.bootBak());attributes.put("boot_change",source.bootChange());
        attributes.put("cpld_bak",source.cpldBak());attributes.put("cpld_change",source.cpldChange());
        attributes.put("pcb_bak",source.pcbBak());attributes.put("pcb_change",source.pcbChange());
        if(write.apply())legacyExtensions.initialize(command.tenantId(),command.actorId(),incoming.getId(),source.extensionDefinitionId(),attributes);
        else legacyExtensions.preview(new cn.iocoder.yudao.module.pms.asset.service.version.SoftwareVersionLegacyExtensionService.Preview(
                command.tenantId(),command.actorId(),incoming.getId(),source.extensionDefinitionId(),attributes));
        return new SourceOutcome("software-versions",String.valueOf(source.sourceId()),"SoftwareVersionRecord","ast_device_software_version_record",incoming.getId(),"SOFTWARE_VERSION_RECORD");
    }
    private static boolean sameHistory(DeviceSoftwareVersionRecordDO a,DeviceSoftwareVersionRecordDO b) {
        if (b==null) return false;
        // MySQL JSON canonicalizes object spacing/order. Equality remains structural, including null/empty values.
        if (!Objects.equals(a.getCustomInfo(),b.getCustomInfo())) {
            if (a.getCustomInfo()==null || b.getCustomInfo()==null
                    || !Objects.equals(JsonUtils.parseTree(a.getCustomInfo()),JsonUtils.parseTree(b.getCustomInfo()))) return false;
        }
        var copy=new DeviceSoftwareVersionRecordDO();org.springframework.beans.BeanUtils.copyProperties(b,copy);
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
    NativeEntityMigrationRow importCorrectedBatch(Command command,SoftwareBatch source) {
        var row=new DeviceSoftwareChangeBatchDO();row.setId(source.mappedRecordId());row.setTenantId(command.tenantId());row.setProjectId(command.projectId());
        row.setCreator(batchActor(command.tenantId(),source.sourceCreatedBy()));row.setCreateTime(source.sourceCreatedAt());
        row.setUpdater(batchActor(command.tenantId(),source.sourceUpdatedBy()));row.setUpdateTime(source.sourceUpdatedAt()==null?source.sourceCreatedAt():source.sourceUpdatedAt());
        if (batches.correctHistoricalAudit(new DeviceSoftwareBatchHistoricalWrite(row))!=1) throw new IllegalStateException("DPPMS_BATCH_AUDIT_CORRECTION_FAILED");
        return importBatch(command,source);
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
        var incoming=new DeviceSoftwareChangeBatchDO();
        incoming.setTenantId(command.tenantId());incoming.setProjectId(command.projectId());
        incoming.setChangeVersion(source.changeVersion());incoming.setChangeRemark(source.changeRemark());incoming.setLatest(source.latest());
        incoming.setCreator(batchActor(command.tenantId(),source.sourceCreatedBy()));incoming.setCreateTime(source.sourceCreatedAt());
        incoming.setUpdater(batchActor(command.tenantId(),source.sourceUpdatedBy()));
        incoming.setUpdateTime(source.sourceUpdatedAt()==null?source.sourceCreatedAt():source.sourceUpdatedAt());
        incoming.setDeleted(false);
        DeviceSoftwareChangeBatchDO row;
        if (source.mappedRecordId()==null) {
            row=incoming;row.setId(write.apply()?IdWorker.getId():-source.sourceId());
            // Explicit XML preserves original NULL actors rather than framework auto-fill.
            if (write.apply() && batches.insertHistorical(new DeviceSoftwareBatchHistoricalWrite(row))!=1) throw new IllegalStateException("DPPMS_BATCH_INSERT_FAILED");
        } else {
            if (source.mappedRecordId()<=0) throw new IllegalArgumentException("DPPMS_BATCH_MAPPING_INVALID");
            incoming.setId(source.mappedRecordId());
            row=batches.selectRecordForUpdate(new DeviceSoftwareBatchIdentityQuery(command.tenantId(),command.projectId(),source.mappedRecordId()));
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
    @Override
    public PageResult<ObservationRecord> pageObservations(Query query) {
        if (!visible(query)) return new PageResult<>(List.of(),0L);
        var page=observations.selectProjectHistoryPage(new DeviceSoftwareProjectHistoryQuery(query.tenantId(),query.projectId(),query.deviceSn(),query.pageNo(),query.pageSize()));
        var rows=new ArrayList<ObservationRecord>();
        for (var row:page.getList()) {
            Map<String,Object> values=JsonUtils.parseObject(JsonUtils.toJsonString(row),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
            if ("DPPMS".equals(row.getDataSource())) {
                var ref=new cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef(query.tenantId(),"AST","SoftwareVersionRecord",row.getId());
                values.put("legacyAttributes",extensions.read(cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef.current(ref),
                        new cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor(query.tenantId(),query.actorId(),"SOFTWARE_HISTORY_READ")).fields());
            }
            rows.add(new ObservationRecord(row.getId(),row.getProjectId(),Collections.unmodifiableMap(values)));
        }
        return new PageResult<>(rows,page.getTotal());
    }
    @Override
    public PageResult<BatchRecord> pageBatches(Query query) {
        if (!visible(query)) return new PageResult<>(List.of(),0L);
        var page=batches.selectPage(new DeviceSoftwareBatchPageQuery(query.tenantId(),query.projectId(),query.pageNo(),query.pageSize()));
        return new PageResult<>(page.getList().stream().map(row->new BatchRecord(row.getId(),row.getProjectId(),
                new BatchValues(row.getChangeVersion(),row.getChangeRemark(),row.getLatest(),row.getCreator(),row.getCreateTime(),
                        row.getUpdater(),row.getUpdateTime()))).toList(),page.getTotal());
    }
    private NativeEntityMigrationRow importFactory(Command command,FactoryVersion source) {
        DeviceFactoryVersionRecordDO row=null;
        if (source.mappedRecordId()!=null) {
            if (source.mappedRecordId()<=0) throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_MAPPING_INVALID");
            row=factories.selectByIdForUpdate(new DeviceFactoryVersionRecordIdentityQuery(command.tenantId(),command.projectId(),source.mappedRecordId()));
            if (row==null || Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(),command.tenantId())
                    || !Objects.equals(row.getProjectId(),command.projectId())
                    || !Objects.equals(row.getDeviceSn(),source.serialNumber()) || !Objects.equals(row.getConpVersion(),source.conp())
                    || !Objects.equals(row.getBootVersion(),source.boot()) || !Objects.equals(row.getCpldVersion(),source.cpld())
                    || !Objects.equals(row.getPcbVersion(),source.pcb()))
                throw new IllegalArgumentException("DPPMS_FACTORY_RECORD_TARGET_CHANGED");
        } else {
            row=new DeviceFactoryVersionRecordDO();
            row.setId(IdWorker.getId());row.setTenantId(command.tenantId());row.setProjectId(command.projectId());
            row.setDeviceSn(source.serialNumber());row.setConpVersion(source.conp());row.setBootVersion(source.boot());
            row.setCpldVersion(source.cpld());row.setPcbVersion(source.pcb());
            if (factories.insert(row)!=1) throw new IllegalStateException("DPPMS_FACTORY_RECORD_INSERT_FAILED");
        }
        return NativeEntityMigrationRow.mapped("factory-versions",source.sourceKey(),target("DeviceFactoryVersionRecord","ast_device_factory_version_record",row.getId(),"FACTORY_VERSION_SOURCE"));
    }
    @Override
    public PageResult<FactoryRecord> pageFactories(Query query) {
        if (!visible(query)) return new PageResult<>(List.of(),0L);
        var page=factories.selectPage(new DeviceFactoryVersionRecordPageQuery(query.tenantId(),query.projectId(),query.deviceSn(),query.pageNo(),query.pageSize()));
        return new PageResult<>(page.getList().stream().map(row->new FactoryRecord(row.getId(),row.getProjectId(),
                new FactoryValues(row.getDeviceSn(),row.getConpVersion(),row.getBootVersion(),row.getCpldVersion(),row.getPcbVersion()))).toList(),page.getTotal());
    }
    private boolean visible(Query query) {
        requireTenant(query.tenantId());
        if (query.actorId()==null||query.projectId()==null||query.pageNo()<1||query.pageSize()<1||query.pageSize()>100)
            throw new IllegalArgumentException("DPPMS_SOURCE_HISTORY_QUERY_INVALID");
        return scope.visibleProjectIds(query.tenantId(),query.actorId()).contains(query.projectId());
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
