package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationHistoricalCarryUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationSourceCodeQuery;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import static cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationHistoricalCarryRules.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Bounded approved correction: existing records, complete raw fields and immutable optimistic guards. */
@Service @RequiredArgsConstructor
public class DppmsInstallationRecordMigrationService implements DppmsInstallationRecordMigrationApi {
    private final InstallationMapper records;
    private final ProjectDeviceSelectionApi devices;
    private final AssetLocationApi locations;
    private final SystemUserMigrationApi users;
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> preview(Command command) {return execute(command,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Command command) {return execute(command,true);}
    private List<Result> execute(Command command,boolean apply) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId())
                || command.actorId()==null || command.actorId()<=0 || command.entries()==null
                || command.entries().isEmpty() || command.entries().size()>100)throw blocked("REQUEST_INVALID");
        var names=new HashSet<String>();
        for(var entry:command.entries()) {
            var raw=entry.originalFields();
            InstallationHistoricalCarryRules.validate(new InstallationHistoricalCarryRules.Source(entry.sourceKey(),command.sourceProjectKey(),raw));
            if(entry.baseline()==null)throw blocked("ORIGINAL_FIELDS_OR_BASELINE_INVALID");
            for(String field:List.of("createBy","updateBy"))if(raw.get(field)!=null&&!raw.get(field).isEmpty())names.add(raw.get(field));
        }
        var identities=users.getHistoricalIdentitiesByUsername(command.tenantId(),names);
        Map<String,Long> actors=new HashMap<>();
        for(var identity:identities)if(identity.id()==null || identity.id()<=0 || !names.contains(identity.username())
                || actors.putIfAbsent(identity.username(),identity.id())!=null)throw blocked("AUDIT_IDENTITY_AMBIGUOUS");
        if(!actors.keySet().equals(names))throw blocked("AUDIT_IDENTITY_UNRESOLVED");
        var lockedRows=new LinkedHashMap<String,InstallationDO>();Set<Long> ids=new HashSet<>();
        for(var entry:command.entries()) {
            var row=records.selectSourceCodeForUpdate(new InstallationSourceCodeQuery(command.tenantId(),command.projectId(),"DPPMS-"+entry.sourceKey()));
            if(row==null || !Objects.equals(row.getId(),entry.baseline().id()) || !ids.add(row.getId())
                    || Boolean.TRUE.equals(row.getDeleted()) || lockedRows.putIfAbsent(entry.sourceKey(),row)!=null)
                throw blocked("RECORD_IDENTITY_INVALID");
            if(!matches(row,entry.baseline().formalFieldsJson()))throw blocked("RECORD_CHANGED");
        }
        // Resolve and lock the same authorized device set once per batch before any installation write.
        // Per-row calls would rescan the contract context after every SQL cache invalidation.
        var deviceIds=lockedRows.values().stream().map(InstallationDO::getEquipmentId).distinct().sorted().toList();
        var selected=devices.validateSelection(command.projectId(),deviceIds);
        Map<Long,String> serials=new HashMap<>();
        if(selected==null)throw blocked("DEVICE_IDENTITY_INVALID");
        for(var device:selected)if(serials.putIfAbsent(device.equipmentId(),device.sn())!=null)throw blocked("DEVICE_IDENTITY_INVALID");
        if(!serials.keySet().equals(new HashSet<>(deviceIds)))throw blocked("DEVICE_IDENTITY_INVALID");
        var results=new ArrayList<Result>();
        for(var entry:command.entries()) {
            var raw=entry.originalFields();var baseline=entry.baseline();var row=lockedRows.get(entry.sourceKey());
            if(!Objects.equals(serials.get(row.getEquipmentId()),raw.get("barcode")))throw blocked("DEVICE_IDENTITY_INVALID");
            for(String field:List.of("siteId","siteVersion","siteLocationId","siteLocationVersion","installerUserId",
                    "environmentCheck","specCheck","photoUrl","result"))
                if(new BeanWrapperImpl(row).getPropertyValue(field)!=null)throw blocked("UNEXPECTED_INSTALLATION_FACT");
            if(!"UNRESOLVED".equals(row.getLocationResolutionStatus()))throw blocked("LOCATION_RESOLUTION_CHANGED");
            var current=records.selectCurrentRecordForUpdate(
                    new cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationCurrentRecordQuery(
                            command.tenantId(),row.getEquipmentId()));
            if(current!=null && Objects.equals(current.getId(),row.getId()))current=null;
            if(!Objects.equals(current==null?null:current.getId(),baseline.currentInstallationId())
                    || current!=null&&!matches(current,baseline.currentFieldsJson()))throw blocked("CURRENT_INSTALLATION_CHANGED");
            var corrected=InstallationHistoricalCarryRules.correct(new InstallationHistoricalCarryRules.Correction(row,current,raw,actors));
            boolean unchanged=matches(row,formalJson(corrected));
            if(!unchanged) {
                if(!matches(row,baseline.formalFieldsJson()) || !Objects.equals(row.getStatus(),0)
                        || row.getInstallTime()!=null || row.getEffectiveFrom()!=null || row.getEffectiveTo()!=null)
                    throw blocked("RECORD_CHANGED");
                corrected.setVersion(Math.addExact(row.getVersion(),1));
            }
            if(apply) {
                if(!unchanged && records.carryHistoricalRecord(new InstallationHistoricalCarryUpdate(corrected,row.getVersion()))!=1)
                    throw blocked("CONCURRENT_CHANGE");
                effectMissingCurrent(new CurrentLocation(locations,corrected,current));
            }
            results.add(new Result(entry.sourceKey(),row.getId(),unchanged?"UNCHANGED":"UPDATED",
                    new Baseline(row.getId(),formalJson(corrected),current==null?null:current.getId(),current==null?null:formalJson(current))));
        }
        return List.copyOf(results);
    }
    private static IllegalArgumentException blocked(String code) {return new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_"+code);}
}
