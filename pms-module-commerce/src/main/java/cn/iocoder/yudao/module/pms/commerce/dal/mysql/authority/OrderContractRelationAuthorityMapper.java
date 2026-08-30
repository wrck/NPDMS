package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityRelationQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderContractRelationAuthorityMapper extends BaseMapperX<SalesOrderContractRelationDO> {
    SalesOrderContractRelationDO selectBySourcePairForUpdate(AuthorityRelationQuery query);
}
