package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationHistoryInsertApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationHistoryInsertMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationCurrentRecordQuery;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationHistoricalCarryRules.*;

/** First insertion only. Existing records continue through their immutable binding/replay path. */
@Service @RequiredArgsConstructor
public class InstallationHistoryInsertService implements InstallationHistoryInsertApi {
    private final InstallationMapper records;
    private final InstallationHistoryInsertMapper inserts;
    private final ProjectDeviceSelectionApi devices;
    private final AssetLocationApi locations;
    private final EngineeringRecordCodeGenerator codes;
    private final SystemUserMigrationApi historicalUsers;
    private final PermissionApi permissions;
    private final AdminUserApi users;

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> preview(Command command){return execute(command,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> insert(Command command){return execute(command,true);}

    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> verify(Verification command) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId())
                || command.actorId()==null || command.actorId()<=0 || command.projectId()==null || command.projectId()<=0
                || command.rows()==null || command.rows().isEmpty())fail("INVALID_COMMAND");
        users.validateUser(command.actorId());
        if(!permissions.hasAnyPermissions(command.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        var results=new ArrayList<Result>();var equipment=new TreeSet<Long>();var ids=new HashSet<Long>();var sourceKeys=new HashSet<String>();
        for(var row:command.rows()) {
            if(row==null || row.sourceKey()==null || !row.sourceKey().matches("[0-9]{1,32}") || !sourceKeys.add(row.sourceKey())
                    || row.installationId()==null || row.installationId()<=0 || !ids.add(row.installationId())
                    || row.formalFieldsJson()==null)fail("BINDING_INVALID");
            var record=inserts.selectBoundRecordForUpdate(new InstallationHistoryInsertMapper.BoundRecord(command.tenantId(),command.projectId(),row.installationId()));
            if(record==null || !Objects.equals(record.getId(),row.installationId()) || Boolean.TRUE.equals(record.getDeleted()) || !Objects.equals(record.getProjectId(),command.projectId())
                    || !Objects.equals(record.getTenantId(),command.tenantId()) || row.equipmentId()==null || !Objects.equals(record.getEquipmentId(),row.equipmentId())
                    || !matches(record,row.formalFieldsJson()))fail("BOUND_RECORD_CHANGED");
            equipment.add(record.getEquipmentId());
            results.add(new Result(row.sourceKey(),record.getId(),record.getCode(),record.getEffectiveTo(),row.formalFieldsJson()));
        }
        var selected=devices.validateSelection(new ProjectDeviceSelectionApi.AuthorizedSelection(command.tenantId(),command.actorId(),command.projectId(),List.copyOf(equipment)));
        if(selected==null || !selected.stream().map(cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice::equipmentId)
                .collect(java.util.stream.Collectors.toSet()).equals(equipment))fail("DEVICE_UNRESOLVED");
        return List.copyOf(results);
    }

    private List<Result> execute(Command command,boolean write) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId())
                || command.actorId()==null || command.actorId()<=0 || command.projectId()==null || command.projectId()<=0
                || command.rows()==null || command.rows().isEmpty())fail("INVALID_COMMAND");
        users.validateUser(command.actorId());
        if(!permissions.hasAnyPermissions(command.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        Set<String> keys=new HashSet<>(),names=new HashSet<>();Set<Long> equipment=new TreeSet<>();
        for(var row:command.rows()) {
            if(row==null || row.equipmentId()==null || row.equipmentId()<=0 || !keys.add(row.sourceKey()))fail("INVALID_MAPPED_ROW");
            var raw=row.originalFields();
            InstallationHistoricalCarryRules.validate(new Source(row.sourceKey(),raw==null?null:raw.get("projectId"),raw));
            if(raw.get("barcode")==null || raw.get("barcode").isBlank() || time(raw.get("createTime"))==null)fail("INVALID_MAPPED_ROW");
            equipment.add(row.equipmentId());
            for(String field:List.of("createBy","updateBy")) {
                var name=raw.get(field);if(name!=null&&!name.isEmpty())names.add(name);
            }
        }
        var actors=new HashMap<String,Long>();
        for(var actor:historicalUsers.getHistoricalIdentitiesByUsername(command.tenantId(),names))
            if(actor.id()==null || actor.id()<=0 || !names.contains(actor.username()) || actors.putIfAbsent(actor.username(),actor.id())!=null)
                fail("AUDIT_IDENTITY_AMBIGUOUS");
        if(!actors.keySet().equals(names))fail("AUDIT_IDENTITY_UNRESOLVED");
        var selected=devices.validateSelection(new ProjectDeviceSelectionApi.AuthorizedSelection(command.tenantId(),command.actorId(),command.projectId(),List.copyOf(equipment)));
        var serials=new HashMap<Long,String>();
        if(selected==null)fail("DEVICE_UNRESOLVED");
        for(var device:selected)if(serials.putIfAbsent(device.equipmentId(),device.sn())!=null)fail("DEVICE_AMBIGUOUS");
        if(!serials.keySet().equals(equipment))fail("DEVICE_UNRESOLVED");
        var current=new HashMap<Long,InstallationDO>();
        for(var id:equipment)current.put(id,records.selectCurrentRecordForUpdate(new InstallationCurrentRecordQuery(command.tenantId(),id)));
        // Source IDs are read in index order. Select each device's latest row locally without sorting the source.
        var latestRows=new LinkedHashMap<Long,Row>();
        for(var row:command.rows()) {
            if(!Objects.equals(serials.get(row.equipmentId()),row.originalFields().get("barcode")))fail("DEVICE_CHANGED");
            latestRows.merge(row.equipmentId(),row,(left,right)->time(left.originalFields().get("createTime"))
                    .isBefore(time(right.originalFields().get("createTime")))?right:left);
        }
        var ordered=new ArrayList<>(latestRows.values());
        for(var row:command.rows())if(latestRows.get(row.equipmentId())!=row)ordered.add(row);
        // Preflight every interval before creating addresses or records, including equal latest timestamps.
        for(var row:ordered) {
            var latest=current.get(row.equipmentId());
            var created=time(row.originalFields().get("createTime"));
            var effective=latest==null?time(latestRows.get(row.equipmentId()).originalFields().get("createTime")):latest.getEffectiveFrom();
            validateInterval(new Interval(created,latest!=null || latestRows.get(row.equipmentId())!=row,effective));
        }
        var result=new ArrayList<Result>();var allocatedCodes=new ArrayList<String>();long previewId=-1;
        for(var row:ordered) {
            var record=new InstallationDO();record.setId(write?IdWorker.getId():previewId--);record.setTenantId(command.tenantId());
            record.setProjectId(command.projectId());record.setEquipmentId(row.equipmentId());
            record.setCode(codes.next(command.projectId(),EngineeringRecordCodeGenerator.INSTALLATION,records,allocatedCodes.toArray(String[]::new)));
            allocatedCodes.add(record.getCode());
            var raw=row.originalFields();var addressText=raw.get("installAddress");
            record.setInstallLocation(addressText);record.setLocationResolutionStatus("UNRESOLVED");
            record.setStatus(0);record.setVersion(0L);record.setDeleted(false);
            var latest=current.get(row.equipmentId());
            if(write) {
                var input=new AddressInput(null,null,null,null,null,null,null,null,null,null,null,addressText,null,null,null,null);
                var reference=locations.maintain(new LocationMaintenanceCommand(command.projectId(),input,null,null,addressText,
                        "INSTALLATION",record.getId().toString(),"0",List.of()));
                var address=locations.getAddress(reference.addressId(),reference.addressVersion());
                if(address==null || !Objects.equals(address.fullAddress(),addressText))fail("ADDRESS_SNAPSHOT_CONFLICT");
                record.setAddressId(reference.addressId());record.setAddressVersion(reference.addressVersion());
                record.setAddressSnapshot(JsonUtils.toJsonString(address));
            } else {
                // Preview supplies only the raw address to the shared rules and never persists a synthetic address.
                record.setAddressId(-1L);record.setAddressVersion(0L);
                record.setAddressSnapshot(JsonUtils.toJsonString(Map.of("fullAddress",addressText)));
            }
            record=InstallationHistoricalCarryRules.correct(new Correction(record,latest,raw,actors));
            if(write) {
                if(inserts.insertHistorical(record)!=1)fail("INSERT_FAILED");
                effectMissingCurrent(new CurrentLocation(locations,record,latest));
            }
            if(latest==null)current.put(row.equipmentId(),record);
            result.add(new Result(row.sourceKey(),record.getId(),record.getCode(),record.getEffectiveTo(),formalJson(record)));
        }
        return List.copyOf(result);
    }
    private static void fail(String code){throw new IllegalArgumentException("INSTALLATION_HISTORY_INSERT_"+code);}
}
