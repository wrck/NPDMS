package cn.iocoder.yudao.module.pms.customer.dal.mysql.classification;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.classification.CustomerMarketRelationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerMarketRelationMapper extends BaseMapperX<CustomerMarketRelationDO> {

    default CustomerMarketRelationDO selectActive(CustomerMarketRelationDO relation) {
        return selectOne(new LambdaQueryWrapperX<CustomerMarketRelationDO>()
                .eq(CustomerMarketRelationDO::getTenantId, relation.getTenantId())
                .eq(CustomerMarketRelationDO::getMarketCode, relation.getMarketCode())
                .eq(CustomerMarketRelationDO::getSystemCode, relation.getSystemCode())
                .eq(CustomerMarketRelationDO::getExpendCode, relation.getExpendCode())
                .eq(CustomerMarketRelationDO::getIndustryCode, relation.getIndustryCode())
                .eq(CustomerMarketRelationDO::getMappingStatus, "ACTIVE"));
    }
}
