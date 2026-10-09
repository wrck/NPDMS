package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.FieldVO;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.support.entity.OwnedBusinessDetailEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.model.DeclaredBusinessFieldValues;
import cn.iocoder.yudao.module.pms.platform.support.persistence.DeclaredBusinessEntityWriter;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntitySaveSupport;
import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.Validator;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.ResolvableType;
import java.lang.reflect.Field;
import java.util.*;

/** Typed ORM mappings declared on the aggregate, never caller-supplied table/property names. */
final class OwnedBusinessDetails {
    private final Field property;
    private final Class<? extends OwnedBusinessDetailEntity> type;
    private final BusinessDetailMapper<OwnedBusinessDetailEntity> mapper;
    private final List<BusinessModelIntrospector.IntrospectedField> fields;

    @SuppressWarnings("unchecked")
    OwnedBusinessDetails(Field property,BeanFactory beans) {
        this.property=property;
        var annotation=property.getAnnotation(BusinessOwnedDetails.class);
        var child=ResolvableType.forField(property).getGeneric(0).resolve();
        var tableField=property.getAnnotation(TableField.class);
        if(child==null || !OwnedBusinessDetailEntity.class.isAssignableFrom(child) || tableField==null || tableField.exist())
            throw invalid("Owned details require a typed transient list of OwnedBusinessDetailEntity");
        type=(Class<? extends OwnedBusinessDetailEntity>)child;
        mapper=(BusinessDetailMapper<OwnedBusinessDetailEntity>)beans.getBean(annotation.mapper());
        if(!type.equals(ResolvableType.forClass(annotation.mapper()).as(BusinessDetailMapper.class).getGeneric(0).resolve()))
            throw invalid("Child Mapper must map the declared detail entity");
        fields=BusinessModelIntrospector.businessFields(type).stream().filter(f->f.property().isAnnotationPresent(BusinessModelField.class)).toList();
    }

    static List<FieldVO> fieldViews(Field property) {
        Class<?> type=ResolvableType.forField(property).getGeneric(0).resolve();
        return BusinessModelIntrospector.businessFields(type).stream().filter(f->f.property().isAnnotationPresent(BusinessModelField.class)).map(f->{
            var a=f.property().getAnnotation(BusinessModelField.class);
            return new FieldVO(f.code(),a.name().isBlank()?f.code():a.name(),f.type().name(),f.required(),a.readable(),a.writable());
        }).toList();
    }

    static Object convert(Object input,BusinessModelIntrospector.IntrospectedField field) {
        if(input==null)return List.of();
        if(!(input instanceof List<?> items))throw invalid("Detail input must be a list");
        Class<?> type=ResolvableType.forField(field.property()).getGeneric(0).resolve();
        var fields=BusinessModelIntrospector.businessFields(type);
        var result=new ArrayList<OwnedBusinessDetailEntity>();
        for(var item:items) {
            if(type.isInstance(item)){result.add((OwnedBusinessDetailEntity)BusinessEntityCopies.copy(item));continue;}
            if(!(item instanceof Map<?,?> values))throw invalid("Invalid detail row");
            try {
                var row=(OwnedBusinessDetailEntity)type.getDeclaredConstructor().newInstance();
                for(var entry:values.entrySet()) {
                    String code=Objects.toString(entry.getKey());
                    if("id".equals(code)){row.setId(identifier(entry.getValue()));continue;}
                    if("version".equals(code)){row.setVersion(identifier(entry.getValue()));continue;}
                    var f=fields.stream().filter(v->v.code().equals(code)).findFirst().orElseThrow(()->invalid("Unknown detail field: "+code));
                    var exposure=f.property().getAnnotation(BusinessModelField.class);
                    if(exposure==null || !exposure.writable())throw invalid("Detail field is not writable: "+code);
                    f.property().set(row,DeclaredBusinessFieldValues.convert(entry.getValue(),f));
                }
                result.add(row);
            } catch(ReflectiveOperationException failure){throw invalid("Detail entity requires an accessible constructor");}
        }
        return result;
    }
    private static Long identifier(Object value){
        if(value==null)return null;
        try{return new java.math.BigDecimal(value.toString()).longValueExact();}catch(RuntimeException e){throw invalid("Invalid detail identity/version");}
    }
    private BusinessDetailMapper.OwnedRows scope(EntityDataRef target){
        var e=target.entity();return new BusinessDetailMapper.OwnedRows(e.tenantId(),e.ownerModule()+"/"+e.entityType()+"/"+property.getName(),e.entityId(),target.revisionId()==null?0L:target.revisionId());
    }
    void load(Object parent,EntityDataRef target){set(parent,mapper.selectOwnedRows(scope(target)));}
    void clearIdentities(Object parent){rows(parent).forEach(row->{row.setId(null);row.setVersion(null);});}
    List<Map<String,Object>> readable(Object parent){
        return rows(parent).stream().map(row->{
            var result=new LinkedHashMap<String,Object>();result.put("id",row.getId());result.put("version",row.getVersion());
            for(var field:fields)if(field.property().getAnnotation(BusinessModelField.class).readable())result.put(field.code(),value(field,row));
            return Collections.unmodifiableMap(result);
        }).toList();
    }
    void save(Object parent,EntityDataRef target,Long userId,Validator validator,boolean copy){
        BusinessEntitySaveSupport.requireTransaction();
        var query=scope(target);var old=new LinkedHashMap<Long,OwnedBusinessDetailEntity>();
        mapper.selectOwnedRows(query).forEach(row->old.put(row.getId(),row));
        var seen=new HashSet<Long>();int order=0;
        for(var row:rows(parent)) {
            if(!type.isInstance(row))throw invalid("Wrong detail type");
            if(copy){row.setId(null);row.setVersion(null);}
            var before=row.getId()==null?null:old.get(row.getId());
            if(row.getId()!=null && (before==null || !seen.add(row.getId())))throw invalid("Detail does not belong to this aggregate or is repeated");
            if(before!=null && !Objects.equals(before.getVersion(),row.getVersion()))throw new BusinessContractException("CONCURRENCY_CONFLICT","Detail version changed");
            if(before!=null)for(var field:fields)if(!field.property().getAnnotation(BusinessModelField.class).writable())setValue(field,row,value(field,before));
            row.setTenantId(query.tenantId());row.setAggregateType(query.aggregateType());row.setAggregateId(query.aggregateId());row.setAggregateRevisionId(query.revisionId());row.setDetailOrder(order++);
            row.setUpdater(userId.toString());row.setUpdateTime(java.time.LocalDateTime.now());
            if(!validator.validate(row).isEmpty())throw invalid("Detail validation failed");
            if(before==null){row.setVersion(0L);row.setCreator(userId.toString());row.setDeleted(false);if(mapper.insert(row)!=1)throw invalid("Detail insert failed");}
            else {
                var nulls=new HashSet<String>();for(var f:fields)if(value(f,row)==null)nulls.add(f.code());
                update(row,before.getVersion(),nulls);
            }
        }
        for(var row:old.values())if(!seen.contains(row.getId())){
            if(mapper.softDeleteOwned(new BusinessDetailMapper.Removal(query,row.getId(),row.getVersion(),userId.toString()))!=1)
                throw new BusinessContractException("CONCURRENCY_CONFLICT","Detail changed before removal");
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private void update(OwnedBusinessDetailEntity row,Long version,Set<String> nulls){DeclaredBusinessEntityWriter.update((com.baomidou.mybatisplus.core.mapper.BaseMapper)mapper,row,row.getTenantId(),version,nulls);}
    @SuppressWarnings("unchecked") private List<OwnedBusinessDetailEntity> rows(Object parent){try{return property.get(parent)==null?List.of():(List<OwnedBusinessDetailEntity>)property.get(parent);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    private void set(Object parent,Object value){try{property.set(parent,value);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    private static Object value(BusinessModelIntrospector.IntrospectedField f,Object row){try{return f.property().get(row);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    private static void setValue(BusinessModelIntrospector.IntrospectedField f,Object row,Object value){try{f.property().set(row,value);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    private static BusinessContractException invalid(String message){return new BusinessContractException("BUSINESS_DETAIL_INVALID",message);}
}
