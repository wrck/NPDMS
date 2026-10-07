package cn.iocoder.yudao.module.pms.platform.dal.mysql.businessconfiguration;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessconfiguration.BusinessFieldConfigurationDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface BusinessFieldConfigurationMapper extends BaseMapperX<BusinessFieldConfigurationDO> {
    record Query(Long tenantId,String ownerModule,String entityType) { }
    record Save(Long tenantId,Long id,long expectedVersion,String fieldsJson,String updater,java.time.LocalDateTime updatedAt) { }
    default BusinessFieldConfigurationDO selectConfiguration(Query query){
        return selectOne(new LambdaQueryWrapperX<BusinessFieldConfigurationDO>().eq(BusinessFieldConfigurationDO::getTenantId,query.tenantId())
                .eq(BusinessFieldConfigurationDO::getOwnerModule,query.ownerModule()).eq(BusinessFieldConfigurationDO::getEntityType,query.entityType()));
    }
    default int updateConfiguration(Save command){
        return update(null,new LambdaUpdateWrapper<BusinessFieldConfigurationDO>()
                .eq(BusinessFieldConfigurationDO::getTenantId,command.tenantId()).eq(BusinessFieldConfigurationDO::getId,command.id())
                .eq(BusinessFieldConfigurationDO::getVersion,command.expectedVersion()).set(BusinessFieldConfigurationDO::getFieldsJson,command.fieldsJson())
                .set(BusinessFieldConfigurationDO::getVersion,Math.incrementExact(command.expectedVersion()))
                .set(BusinessFieldConfigurationDO::getUpdater,command.updater()).set(BusinessFieldConfigurationDO::getUpdateTime,command.updatedAt()));
    }
}
