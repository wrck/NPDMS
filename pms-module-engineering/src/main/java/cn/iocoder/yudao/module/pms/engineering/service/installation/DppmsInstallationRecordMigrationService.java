package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationHistoricalCarryUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationSourceCodeQuery;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Bounded approved correction: existing records, complete raw fields and immutable optimistic guards. */
@Service @RequiredArgsConstructor
public class DppmsInstallationRecordMigrationService implements DppmsInstallationRecordMigrationApi {
    private final InstallationMapper records;
    private final ProjectDeviceSelectionApi devices;
    private final AssetLocationApi locations;
    private final SystemUserMigrationApi users;
    private static final List<String> FORMAL_FIELDS=List.of("id","tenant_id","project_id","code","equipment_id","install_location",
            "address_id","address_version","site_id","site_version","site_location_id","site_location_version",
            "location_resolution_status","address_snapshot","location_snapshot","effective_from","effective_to","install_time",
            "installer_user_id","environment_check","spec_check","photo_url","result","status","remark","version",
            "creator","create_time","updater","update_time","deleted");
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
            if(raw==null || !raw.keySet().equals(Set.copyOf(ORIGINAL_COLUMNS))
                    || entry.sourceKey()==null || !entry.sourceKey().matches("[0-9]{1,32}")
                    || !Objects.equals(raw.get("id"),entry.sourceKey()) || !Objects.equals(raw.get("projectId"),command.sourceProjectKey())
                    || raw.get("installAddress")==null || raw.get("installAddress").isBlank() || raw.get("installAddress").length()>255
                    || !"-1".equals(raw.get("transferFlag")) || entry.baseline()==null)
                throw blocked("ORIGINAL_FIELDS_OR_BASELINE_INVALID");
            for(String field:List.of("chProjectId","chContractNo","transferProjectId","transferContractNo"))
                if(raw.get(field)!=null)throw blocked("TRANSFER_REQUIRES_ADJUDICATION");
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
            LocalDateTime created=time(raw.get("createTime"));
            if(created==null || current!=null&&(current.getEffectiveFrom()==null || !created.isBefore(current.getEffectiveFrom())))
                throw blocked("EFFECTIVE_DATE_REQUIRES_ADJUDICATION");
            var corrected=new InstallationDO();var original=new BeanWrapperImpl(row);var next=new BeanWrapperImpl(corrected);
            for(String field:FORMAL_FIELDS)next.setPropertyValue(camel(field),original.getPropertyValue(camel(field)));
            corrected.setStatus(2);corrected.setInstallLocation(raw.get("installAddress"));
            corrected.setInstallTime(created);corrected.setEffectiveFrom(created);
            corrected.setEffectiveTo(current==null?null:current.getEffectiveFrom());corrected.setRemark(null);
            corrected.setCreator(actor(raw.get("createBy"),actors));corrected.setCreateTime(created);
            corrected.setUpdater(actor(raw.get("updateBy"),actors));
            corrected.setUpdateTime(raw.get("updateTime")==null?created:time(raw.get("updateTime")));
            if(row.getAddressId()==null || row.getAddressVersion()==null || row.getAddressSnapshot()==null
                    || !Objects.equals(raw.get("installAddress"),JsonUtils.parseObject(row.getAddressSnapshot(),Map.class).get("fullAddress")))
                throw blocked("ADDRESS_BUSINESS_SNAPSHOT_INVALID");
            var snapshot=new LinkedHashMap<String,Object>();snapshot.put("address",JsonUtils.parseObject(row.getAddressSnapshot(),Map.class));
            snapshot.put("site",null);snapshot.put("siteLocation",null);snapshot.put("fallbackLocation",raw.get("installAddress"));
            corrected.setLocationSnapshot(JsonUtils.toJsonString(snapshot));
            corrected.setVersion(row.getVersion());
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
                if(current==null)locations.effectEquipmentLocationIfAbsent(new EquipmentLocationEffectiveCommand(
                        row.getEquipmentId(),row.getId(),null,null,corrected.getInstallLocation(),
                        corrected.getLocationResolutionStatus(),corrected.getLocationSnapshot(),created));
            }
            results.add(new Result(entry.sourceKey(),row.getId(),unchanged?"UNCHANGED":"UPDATED",
                    new Baseline(row.getId(),formalJson(corrected),current==null?null:current.getId(),current==null?null:formalJson(current))));
        }
        return List.copyOf(results);
    }
    private static String actor(String original,Map<String,Long> actors) {return original==null||original.isEmpty()?original:actors.get(original).toString();}
    private static LocalDateTime time(String value) {return value==null?null:LocalDateTime.parse(value.replace(' ','T'));}
    private static String camel(String field) {
        var parts=field.split("_");var name=new StringBuilder(parts[0]);
        for(int i=1;i<parts.length;i++)name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        return name.toString();
    }
    private static String value(Object value) {return value==null?null:value.toString();}
    private static boolean matches(InstallationDO row,String expectedJson) {
        if(expectedJson==null)return false;
        var expected=JsonUtils.parseObject(expectedJson,new TypeReference<Map<String,String>>(){});
        if(!expected.keySet().containsAll(FORMAL_FIELDS))return false;
        var bean=new BeanWrapperImpl(row);
        for(String field:FORMAL_FIELDS) {
            Object actual=bean.getPropertyValue(camel(field));String wanted=expected.get(field);
            if(actual instanceof LocalDateTime stamp) {if(wanted==null || !stamp.equals(time(wanted)))return false;}
            else if(actual instanceof Boolean flag) {
                if(wanted==null || !(flag?Set.of("true","01","1"):Set.of("false","00","0")).contains(wanted))return false;
            } else if(!Objects.equals(value(actual),wanted))return false;
        }
        return true;
    }
    private static String formalJson(InstallationDO row) {
        var bean=new BeanWrapperImpl(row);
        return FORMAL_FIELDS.stream().map(field->JsonUtils.toJsonString(field)+":"+JsonUtils.toJsonString(value(bean.getPropertyValue(camel(field)))))
                .collect(Collectors.joining(",","{","}"));
    }
    private static IllegalArgumentException blocked(String code) {return new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_"+code);}
}
