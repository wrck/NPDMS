package cn.iocoder.yudao.module.pms.platform.service.business;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessconfiguration.BusinessFieldConfigurationDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessconfiguration.BusinessFieldConfigurationMapper;
import cn.iocoder.yudao.module.pms.platform.support.business.BusinessFieldConfigurations;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class BusinessFieldConfigurationService implements BusinessFieldConfigurationApi {
    private final BusinessFieldConfigurationMapper mapper;
    private final DirectBusinessOwners owners;
    private final BusinessCallerContext callers;
    private final cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi audit;
    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.ModelDetailVO authorize(Identity identity,EntityActor actor,boolean write){
        var caller=callers.require();
        if(actor==null || !Objects.equals(actor.tenantId(),caller.tenantId()) || !Objects.equals(actor.userId(),caller.userId()))throw denied("Trusted caller required");
        if(identity==null)throw denied("Business identity required");
        return owners.byIdentity(identity.ownerModule(),identity.entityType()).orElseThrow(()->denied("Unknown inherited business")).configurationModel(write);
    }
    @Override @Transactional(readOnly=true)
    public Configuration read(Identity identity,EntityActor actor){
        authorize(identity,actor,false);var row=mapper.selectConfiguration(new BusinessFieldConfigurationMapper.Query(actor.tenantId(),identity.ownerModule(),identity.entityType()));
        return row==null?new Configuration(0,List.of()):new Configuration(row.getVersion(),JsonUtils.parseArray(row.getFieldsJson(),Field.class));
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public Configuration save(Identity identity,EntityActor actor,long expectedVersion,List<Field> fields){
        var model=authorize(identity,actor,true);BusinessFieldConfigurations.validate(model,fields);
        if(expectedVersion<0)throw denied("Invalid configuration version");
        var row=mapper.selectConfiguration(new BusinessFieldConfigurationMapper.Query(actor.tenantId(),identity.ownerModule(),identity.entityType()));
        String json=JsonUtils.toJsonString(fields);var now=java.time.LocalDateTime.now();
        if(row==null){
            if(expectedVersion!=0)throw conflict();
            row=new BusinessFieldConfigurationDO();row.setId(IdWorker.getId());row.setTenantId(actor.tenantId());row.setOwnerModule(identity.ownerModule());row.setEntityType(identity.entityType());row.setFieldsJson(json);row.setVersion(1L);row.setCreator(actor.userId().toString());row.setUpdater(actor.userId().toString());row.setCreateTime(now);row.setUpdateTime(now);
            try{if(mapper.insert(row)!=1)throw conflict();}catch(org.springframework.dao.DuplicateKeyException race){throw conflict();}
        }else if(mapper.updateConfiguration(new BusinessFieldConfigurationMapper.Save(actor.tenantId(),row.getId(),expectedVersion,json,actor.userId().toString(),now))!=1)throw conflict();
        audit.record(actor.tenantId(),actor.userId(),actor.correlationId(),"BUSINESS_FIELD_CONFIGURATION_SAVE",identity.entityType(),identity.ownerModule()+":"+identity.entityType(),"SUCCESS",Map.of("version",Math.incrementExact(expectedVersion),"fieldCount",fields.size()));
        return new Configuration(Math.incrementExact(expectedVersion),fields);
    }
    private static BusinessContractException conflict(){return new BusinessContractException("FIELD_CONFIGURATION_CONFLICT","Field configuration changed; reload before saving");}
    private static BusinessContractException denied(String message){return new BusinessContractException("ACCESS_DENIED",message);}
}
