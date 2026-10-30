package cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface ConfigurationCollectionOwnerMapper extends BaseMapperX<ConfigurationDO> {
    ConfigurationDO lockById(@Param("id") Long id);
}
