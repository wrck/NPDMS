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
            if(Boolean.TRUE.equals(setting.listVisible())&&!field.readable()
                    ||Boolean.TRUE.equals(setting.searchable())&&!field.searchable()
                    ||Boolean.TRUE.equals(setting.sortable())&&!field.sortable())throw invalid("Configuration cannot expand field capabilities");
        }
    }
    public static BusinessModelViews.ModelDetailVO apply(BusinessModelViews.ModelDetailVO model,BusinessFieldConfigurationApi.Configuration settings){
        var configured=new HashMap<String,BusinessFieldConfigurationApi.Field>();settings.fields().forEach(field->configured.put(field.code(),field));
        var fields=model.fields().stream().map(field->{
            var setting=configured.get(field.code());if(setting==null)return field;
            return new BusinessModelViews.FieldVO(field.code(),setting.label()==null?field.name():setting.label(),field.type(),field.required(),field.readable(),field.writable(),
                    setting.displayOrder()==null?field.displayOrder():setting.displayOrder(),field.readable()&&(setting.listVisible()==null?field.listVisible():setting.listVisible()),
                    field.searchable()&&!Boolean.FALSE.equals(setting.searchable()),field.sortable()&&!Boolean.FALSE.equals(setting.sortable()));
        }).sorted(Comparator.comparingInt(BusinessModelViews.FieldVO::displayOrder)).toList();
        return new BusinessModelViews.ModelDetailVO(model.ownerModule(),model.entityType(),model.stableCode(),model.title(),model.viewCode(),fields,model.operations(),model.capabilities());
    }
    private static BusinessContractException invalid(String message){return new BusinessContractException("FIELD_CONFIGURATION_INVALID",message);}
}
