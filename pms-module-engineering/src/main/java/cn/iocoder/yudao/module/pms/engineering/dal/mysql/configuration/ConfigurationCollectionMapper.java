package cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationCollectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.query.ConfigurationCollectionPageQuery;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface ConfigurationCollectionMapper extends BaseMapperX<ConfigurationCollectionDO> {
    default ConfigurationCollectionDO findRequest(Long tenantId, String requestKey) {
        return selectOne(new LambdaQueryWrapperX<ConfigurationCollectionDO>()
                .eq(ConfigurationCollectionDO::getTenantId, tenantId).eq(ConfigurationCollectionDO::getRequestKey, requestKey));
    }
    default PageResult<ConfigurationCollectionDO> page(ConfigurationCollectionPageQuery query) {
        PageParam page = new PageParam(); page.setPageNo(query.pageNo()); page.setPageSize(query.pageSize());
        return selectPage(page, new LambdaQueryWrapperX<ConfigurationCollectionDO>()
                .eq(ConfigurationCollectionDO::getTenantId, query.tenantId())
                .eq(ConfigurationCollectionDO::getConfigurationId, query.configurationId())
                .orderByDesc(ConfigurationCollectionDO::getId));
    }
}
