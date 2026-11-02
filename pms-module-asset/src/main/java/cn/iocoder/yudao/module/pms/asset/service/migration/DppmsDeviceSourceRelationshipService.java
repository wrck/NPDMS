package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceSourceRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceSourceRelationshipMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DppmsDeviceSourceRelationshipService implements DppmsDeviceSourceRelationshipApi {
    private final DeviceSourceRelationshipMapper relationships;
    private final DeviceMapper devices;
    private final DeviceAccessScopeService scope;
    private final ProjectContractQueryApi contracts;
    private final PermissionApi permissions;

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<AuditResult> previewAuditCorrection(AuditRequest request) { return correctAudit(request,false); }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<AuditResult> applyAuditCorrection(AuditRequest request) { return correctAudit(request,true); }

    private List<AuditResult> correctAudit(AuditRequest request,boolean apply) {
        if(request==null) fail("AUDIT_REQUEST_INVALID");
        requireTenant(request.tenantId());
        if(request.actorId()==null || request.actorId()<=0 || request.owner()==null
                || !request.owner().matches("integration:"+request.tenantId()+":[0-9]+")
                || request.entries()==null || request.entries().isEmpty() || request.entries().size()>500
                || !permissions.hasAnyPermissions(request.actorId(),"pms:integration:execute")) fail("AUDIT_CORRECTION_FORBIDDEN");
        var keys=new HashSet<Long>();var targets=new HashSet<Long>();var results=new ArrayList<AuditResult>();
        for(var entry:request.entries()) {
            if(entry==null) fail("AUDIT_ENTRY_INVALID");
            validateSource(entry.source());
            if(!keys.add(entry.source().sourceId()) || entry.targetId()==null || entry.targetId()<=0
                    || !targets.add(entry.targetId()) || entry.expectedBeforeFingerprint()==null
                    || !entry.expectedBeforeFingerprint().matches("[0-9a-f]{64}")) fail("AUDIT_ENTRY_INVALID");
            var expected=entity(new Command(request.tenantId(),request.actorId(),List.of()),entry.source());
            var row=relationships.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(request.tenantId(),
                    "DPPMS",entry.source().sourceId().toString()));
            if(row==null || !Objects.equals(row.getId(),entry.targetId()) || Boolean.TRUE.equals(row.getDeleted())
                    || row.getVersion()==null || !sameFact(row,expected)) fail("AUDIT_IDENTITY_CHANGED");
            var before=auditRecord(row);
            if(!before.fingerprint().equals(entry.expectedBeforeFingerprint())) fail("AUDIT_FULL_ROW_CHANGED");
            boolean changed=!Objects.equals(before.createTime(),expected.getCreateTime())
                    || !Objects.equals(before.updateTime(),expected.getUpdateTime());
            var after=new AuditRecord(before.id(),before.tenantId(),before.sourceDeviceSn(),before.targetDeviceSn(),
                    before.sourceItem(),before.targetItem(),before.relationshipType(),before.contractNo(),before.effectiveFrom(),
                    before.effectiveTo(),before.sourceSystem(),before.sourceKey(),before.sourceVersion(),
                    changed?Math.addExact(before.version(),1):before.version(),before.creator(),expected.getCreateTime(),
                    before.updater(),expected.getUpdateTime(),before.deleted());
            results.add(new AuditResult(entry.source().sourceId().toString(),changed?"UPDATED":"UNCHANGED",before,after));
        }
        // Lock and check the entire chunk before its first write. INT owns the same transaction's evidence/bindings.
        if(apply) for(var result:results) if("UPDATED".equals(result.action())
                && relationships.correctHistoricalAudit(new DeviceSourceRelationshipMapper.HistoricalAuditWrite(
                    request.tenantId(),result.before().id(),result.before().version(),result.after().createTime(),
                    result.after().updateTime()))!=1) fail("AUDIT_WRITE_CONFLICT");
        return List.copyOf(results);
    }

    private static AuditRecord auditRecord(DeviceSourceRelationshipDO r) {
        return new AuditRecord(r.getId(),r.getTenantId(),r.getSourceDeviceSn(),r.getTargetDeviceSn(),r.getSourceItem(),
                r.getTargetItem(),r.getRelationshipType(),r.getContractNo(),r.getEffectiveFrom(),r.getEffectiveTo(),
                r.getSourceSystem(),r.getSourceKey(),r.getSourceVersion(),r.getVersion(),r.getCreator(),r.getCreateTime(),
                r.getUpdater(),r.getUpdateTime(),r.getDeleted());
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<NativeEntityMigrationRow> importRelationships(Command command) {
        return executeImport(command,true).stream().map(result -> NativeEntityMigrationRow.mapped("device-relations",result.sourceKey(),
                new ExternalTargetMapping("AST","DeviceRelationship","ast_device_relationship",
                        result.after().id(),"SOURCE_DEVICE_RELATION",0))).toList();
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<ImportResult> previewRelationships(Command command) { return executeImport(command,false); }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<ImportResult> applyRelationships(Command command) { return executeImport(command,true); }

    private List<ImportResult> executeImport(Command command,boolean apply) {
        if (command==null) fail("COMMAND_INVALID");
        requireTenant(command.tenantId());
        if (command.actorId()==null || command.actorId()<=0 || command.relations()==null
                || command.relations().isEmpty() || command.relations().size()>2000) fail("COMMAND_INVALID");
        if (!permissions.hasAnyPermissions(command.actorId(),"pms:integration:execute")) fail("IMPORT_FORBIDDEN");
        var keys=new HashSet<Long>();
        // Validate every source before any write. Existing-row differences are never absorbed by ordinary import.
        for (var source:command.relations()) {
            validateSource(source);
            if (!keys.add(source.sourceId())) fail("DUPLICATE_SOURCE_ID");
        }
        var result=new ArrayList<ImportResult>();
        for (var source:command.relations()) {
            var expected=entity(command,source);
            var row=relationships.selectSourceForUpdate(new DeviceSourceRelationshipIdentityQuery(
                    command.tenantId(),"DPPMS",expected.getSourceKey()));
            var before=row==null?null:record(row);
            if (row==null) {
                row=expected;
                if (apply) row.setId(IdWorker.getId());
                if (apply && relationships.insertHistorical(new DeviceSourceRelationshipMapper.HistoricalInsert(row))!=1)
                    fail("INSERT_FAILED");
            } else {
                if (Boolean.TRUE.equals(row.getDeleted()) || !sameFact(row,expected)) fail("EXISTING_FACT_CHANGED");
                if (!Objects.equals(row.getCreateTime(),expected.getCreateTime())
                        || !Objects.equals(row.getUpdateTime(),expected.getUpdateTime()))
                    fail("EXISTING_AUDIT_REQUIRES_CORRECTION");
            }
            result.add(new ImportResult(expected.getSourceKey(),before==null?"CREATE":"UNCHANGED",before,record(row)));
        }
        return List.copyOf(result);
    }

    @Override
    public PageResult<RelationshipRecord> pageRelationships(Query query) {
        if (query==null) fail("QUERY_INVALID");
        requireTenant(query.tenantId());
        if (query.actorId()==null || query.actorId()<=0 || query.pageNo()<1 || query.pageSize()<1 || query.pageSize()>100
                || (query.projectId()==null)==(query.deviceId()==null)
                || (query.projectId()!=null && query.projectId()<=0)
                || (query.deviceId()!=null && query.deviceId()<=0)
                || (query.deviceSn()!=null && query.deviceSn().isBlank())) fail("QUERY_INVALID");
        String sn=query.deviceSn();
        Set<String> numbers=Set.of();
        if (query.deviceId()!=null) {
            scope.assertReadable(new DeviceAccessScopeService.ReadAccess(query.tenantId(),query.actorId(),query.deviceId()));
            var device=devices.selectByTenantAndId(query.tenantId(),query.deviceId());
            if (device==null || !Objects.equals(device.getTenantId(),query.tenantId())
                    || Boolean.TRUE.equals(device.getDeleted()) || device.getSn()==null
                    || (sn!=null && !sn.equals(device.getSn()))) fail("DEVICE_CHANGED");
            sn=device.getSn();
        } else {
            if (!scope.visibleProjectIds(query.tenantId(),query.actorId()).contains(query.projectId()))
                return PageResult.empty();
            numbers=contracts.getCurrentContractNumbers(query.projectId());
            if (numbers==null || numbers.isEmpty()) return PageResult.empty();
        }
        var condition=new DeviceSourceRelationshipPageQuery(query.tenantId(),query.projectId(),numbers,sn,
                ((long)query.pageNo()-1)*query.pageSize(),query.pageSize());
        long count=relationships.countRelationships(condition);
        if (count==0) return PageResult.empty();
        var rows=relationships.selectRelationshipPage(condition);
        for (var row:rows) if (!Objects.equals(row.getTenantId(),query.tenantId()) || Boolean.TRUE.equals(row.getDeleted()))
            fail("QUERY_TENANT_MISMATCH");
        return new PageResult<>(rows.stream().map(DppmsDeviceSourceRelationshipService::record).toList(),count);
    }

    private static DeviceSourceRelationshipDO entity(Command command,Relation source) {
        var row=new DeviceSourceRelationshipDO();
        row.setTenantId(command.tenantId());row.setSourceDeviceSn(source.sn1());row.setTargetDeviceSn(source.sn2());
        row.setSourceItem(source.item1());row.setTargetItem(source.item2());row.setContractNo(source.contract());
        row.setRelationshipType("EXTRA_SN");row.setSourceSystem("DPPMS");row.setSourceKey(source.sourceId().toString());
        row.setSourceVersion(source.sourceUpdateTime());row.setVersion(0);row.setDeleted(false);
        var created=parseDate(source.sourceCreateTime());
        var updated=source.sourceUpdateTime()==null?created:parseDate(source.sourceUpdateTime());
        row.setEffectiveFrom(created);row.setCreateTime(created);row.setUpdateTime(updated);
        row.setCreator(command.actorId().toString());row.setUpdater(command.actorId().toString());
        return row;
    }
    private static void validateSource(Relation source) {
        if (source==null || source.sourceId()==null || source.sourceId()<=0
                || !text(source.sn1(),100,false) || !text(source.sn2(),100,false)
                || !text(source.item1(),15,true) || !text(source.item2(),15,true)
                || !text(source.contract(),64,true) || !text(source.sourceUpdateTime(),64,true)) fail("SOURCE_INVALID");
        if (source.sn1().equalsIgnoreCase(source.sn2())) fail("SELF_RELATION_PENDING_DECISION");
        parseDate(source.sourceCreateTime());
        if (source.sourceUpdateTime()!=null) parseDate(source.sourceUpdateTime());
    }
    private static boolean text(String value,int length,boolean nullable) {
        return value==null?nullable:value.length()<=length && (nullable || !value.isBlank());
    }
    static LocalDateTime parseDate(String value) {
        if (value==null || value.isBlank()) fail("SOURCE_DATE_INVALID");
        try {
            var date=LocalDateTime.parse(value.replace(' ','T'),DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (date.getNano()%1_000_000!=0) fail("SOURCE_DATE_PRECISION");
            return date;
        } catch (DateTimeParseException ex) {
            fail("SOURCE_DATE_INVALID");return null;
        }
    }
    private static boolean sameFact(DeviceSourceRelationshipDO a,DeviceSourceRelationshipDO e) {
        return Objects.equals(a.getTenantId(),e.getTenantId())
                && Objects.equals(a.getSourceDeviceSn(),e.getSourceDeviceSn())
                && Objects.equals(a.getTargetDeviceSn(),e.getTargetDeviceSn())
                && Objects.equals(a.getSourceItem(),e.getSourceItem())
                && Objects.equals(a.getTargetItem(),e.getTargetItem())
                && Objects.equals(a.getContractNo(),e.getContractNo())
                && Objects.equals(a.getRelationshipType(),e.getRelationshipType())
                && Objects.equals(a.getSourceSystem(),e.getSourceSystem())
                && Objects.equals(a.getSourceKey(),e.getSourceKey())
                && Objects.equals(a.getSourceVersion(),e.getSourceVersion())
                && Objects.equals(a.getEffectiveFrom(),e.getEffectiveFrom()) && a.getEffectiveTo()==null;
    }
    private static RelationshipRecord record(DeviceSourceRelationshipDO row) {
        return new RelationshipRecord(row.getId(),row.getSourceDeviceSn(),row.getTargetDeviceSn(),row.getSourceItem(),
                row.getTargetItem(),row.getRelationshipType(),row.getContractNo(),row.getEffectiveFrom(),row.getEffectiveTo(),
                row.getSourceSystem(),row.getSourceKey(),row.getSourceVersion(),row.getVersion(),row.getCreator(),
                row.getCreateTime(),row.getUpdater(),row.getUpdateTime());
    }
    private static void requireTenant(Long tenantId) {
        if (!Objects.equals(tenantId,TenantContextHolder.getRequiredTenantId())) fail("TENANT_MISMATCH");
    }
    private static void fail(String reason) {throw new IllegalArgumentException("DPPMS_DEVICE_RELATION_"+reason);}
}
