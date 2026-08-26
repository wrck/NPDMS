package cn.iocoder.yudao.module.pms.customer.dal.mysql.security;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.security.CustomerScopeSliceDO;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeSliceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerScopeSliceMapper extends BaseMapperX<CustomerScopeSliceDO> {

    List<CustomerScopeSliceDO> selectEffective(@Param("query") CustomerScopeSliceQuery query);
}
