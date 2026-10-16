package cn.iocoder.yudao.module.pms.platform.support.business;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews;
import java.util.*;
/** Runtime presentation may narrow code-owned capabilities, never grant read/write or SQL access. */
public final class BusinessFieldConfigurations {
    private BusinessFieldConfigurations(){ }
    public static void validate(BusinessModelViews.ModelDetailVO model,List<BusinessFieldConfigurationApi.Field> settings){
        if(settings==null || settings.size()>200)throw invalid("Invalid field configuration");
        var known=new HashMap<String,BusinessModelViews.FieldVO>();model.fields().forEach(field->known.put(field.code(),field));
        var seen=new HashSet<String>();
        for(var setting:settings){
            if(setting==null || !seen.add(setting.code()) || !known.containsKey(setting.code()))throw invalid("Unknown or duplicate field");
            var field=known.get(setting.code());
            if(setting.label()!=null && (setting.label().isBlank() || setting.label().length()>128))throw invalid("Invalid field label");
            if((Boolean.TRUE.equals(setting.listVisible()) || Boolean.TRUE.equals(setting.formVisible()) || Boolean.TRUE.equals(setting.tabVisible()))&&!field.readable()
                    ||Boolean.TRUE.equals(setting.searchable())&&!field.searchable()
                    ||Boolean.TRUE.equals(setting.sortable())&&!field.sortable())throw invalid("Configuration cannot expand field capabilities");
        }
    }
    public static void validate(BusinessModelViews.ModelDetailVO model,BusinessFieldConfigurationApi.Configuration settings){
        validate(model,settings.fields());
        if(settings.profiles().size()>50)throw invalid("Too many named configurations");
        var keys=new HashSet<String>();
        for(var profile:settings.profiles()){
            if(profile==null || profile.key()==null || !profile.key().matches("[A-Za-z0-9_-]{1,64}") || !keys.add(profile.key())
                    || profile.name()==null || profile.name().isBlank() || profile.name().length()>128
                    || profile.formRevisionId()!=null && profile.formRevisionId()<=0)throw invalid("Invalid named configuration");
            validate(model,profile.fields());
        }
        if(settings.defaultProfile()!=null && !keys.contains(settings.defaultProfile()))throw invalid("Default configuration is unavailable");
    }
    public static BusinessModelViews.ModelDetailVO apply(BusinessModelViews.ModelDetailVO model,BusinessFieldConfigurationApi.Configuration settings){
        return apply(model,settings,null);
    }
    public static BusinessModelViews.ModelDetailVO apply(BusinessModelViews.ModelDetailVO model,BusinessFieldConfigurationApi.Configuration settings,String selected){
        String key=selected==null?settings.defaultProfile():selected;
        var profile=key==null || key.isBlank()?null:settings.profiles().stream().filter(value->value.key().equals(key)).findFirst().orElseThrow(()->invalid("Named configuration is unavailable"));
        var overrides=profile==null?settings.fields():profile.fields();
        var features=profile==null || profile.features()==null?settings.features():profile.features();
        var configured=new HashMap<String,BusinessFieldConfigurationApi.Field>();overrides.forEach(field->configured.put(field.code(),field));
        var fields=model.fields().stream().map(field->{
            var setting=configured.get(field.code());if(setting==null)return field;
            return new BusinessModelViews.FieldVO(field.code(),setting.label()==null?field.name():setting.label(),field.type(),field.required(),field.readable(),field.writable(),
                    setting.displayOrder()==null?field.displayOrder():setting.displayOrder(),field.readable()&&(setting.listVisible()==null?field.listVisible():setting.listVisible()),
                    field.searchable()&&!Boolean.FALSE.equals(setting.searchable()),field.sortable()&&!Boolean.FALSE.equals(setting.sortable()),field.children(),
                    field.readable()&&!Boolean.FALSE.equals(setting.formVisible()),field.readable()&&!Boolean.FALSE.equals(setting.tabVisible()));
        }).sorted(Comparator.comparingInt(BusinessModelViews.FieldVO::displayOrder)).toList();
        return new BusinessModelViews.ModelDetailVO(model.ownerModule(),model.entityType(),model.stableCode(),model.title(),model.viewCode(),fields,model.operations(),model.capabilities(),features,
                profile == null ? null : profile.formRevisionId());
    }
    private static BusinessContractException invalid(String message){return new BusinessContractException("FIELD_CONFIGURATION_INVALID",message);}
}
