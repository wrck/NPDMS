package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectInstallationRow;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi;
import org.springframework.beans.BeanWrapperImpl;
import tools.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** The existing approved carry rules, shared by correction and first insertion. */
final class DirectInstallationCarryRules {
    private DirectInstallationCarryRules() {}
    static final List<String> FORMAL_FIELDS=List.of("id","tenant_id","project_id","code","equipment_id","install_location",
            "address_id","address_version","site_id","site_version","site_location_id","site_location_version",
            "location_resolution_status","address_snapshot","location_snapshot","effective_from","effective_to","install_time",
            "installer_user_id","environment_check","spec_check","photo_url","result","status","remark","version",
            "creator","create_time","updater","update_time","deleted");

    record Source(String sourceKey,String sourceProjectKey,Map<String,String> fields) {}
    static void validate(Source source) {
        var raw=source.fields();
        if(raw==null || !raw.keySet().equals(Set.copyOf(DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS))
                || source.sourceKey()==null || !source.sourceKey().matches("[0-9]{1,32}")
                || !Objects.equals(raw.get("id"),source.sourceKey()) || !Objects.equals(raw.get("projectId"),source.sourceProjectKey())
                || raw.get("installAddress")==null || raw.get("installAddress").isBlank() || raw.get("installAddress").length()>255
                || !"-1".equals(raw.get("transferFlag")))throw blocked("ORIGINAL_FIELDS_OR_BASELINE_INVALID");
        for(String field:List.of("chProjectId","chContractNo","transferProjectId","transferContractNo"))
            if(raw.get(field)!=null)throw blocked("TRANSFER_REQUIRES_ADJUDICATION");
    }
    record Correction(DirectInstallationRow record,DirectInstallationRow current,Map<String,String> original,Map<String,Long> actors) {}
    static DirectInstallationRow correct(Correction correction) {
        var row=correction.record();var current=correction.current();var raw=correction.original();var actors=correction.actors();
        LocalDateTime created=time(raw.get("createTime"));
        validateInterval(new Interval(created,current!=null,current==null?null:current.getEffectiveFrom()));
        var corrected=new DirectInstallationRow();var original=new BeanWrapperImpl(row);var next=new BeanWrapperImpl(corrected);
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
        return corrected;
    }
    record Interval(LocalDateTime created,boolean hasCurrent,LocalDateTime currentFrom) {}
    static void validateInterval(Interval interval) {
        if(interval.created()==null || interval.hasCurrent() && (interval.currentFrom()==null || !interval.created().isBefore(interval.currentFrom())))
            throw blocked("EFFECTIVE_DATE_REQUIRES_ADJUDICATION");
    }
    record CurrentLocation(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi owner,DirectInstallationRow record,DirectInstallationRow current) {}
    static void effectMissingCurrent(CurrentLocation request) {
        var record=request.record();
        if(request.current()==null)request.owner().effectEquipmentLocationIfAbsent(
                new cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand(
                        record.getEquipmentId(),record.getId(),null,null,record.getInstallLocation(),
                        record.getLocationResolutionStatus(),record.getLocationSnapshot(),record.getEffectiveFrom()));
    }
    static String actor(String original,Map<String,Long> actors) {return original==null||original.isEmpty()?original:actors.get(original).toString();}
    static LocalDateTime time(String value) {return value==null?null:LocalDateTime.parse(value.replace(' ','T'));}
    static String camel(String field) {
        var parts=field.split("_");var name=new StringBuilder(parts[0]);
        for(int i=1;i<parts.length;i++)name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        return name.toString();
    }
    static String value(Object value) {return value==null?null:value.toString();}
    static boolean matches(DirectInstallationRow row,String expectedJson) {
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
    static String formalJson(DirectInstallationRow row) {
        var bean=new BeanWrapperImpl(row);
        return FORMAL_FIELDS.stream().map(field->JsonUtils.toJsonString(field)+":"+JsonUtils.toJsonString(value(bean.getPropertyValue(camel(field)))))
                .collect(Collectors.joining(",","{","}"));
    }
    private static IllegalArgumentException blocked(String code) {return new IllegalArgumentException("DPPMS_INSTALLATION_RECORD_"+code);}
}
