package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.SalesOrderAuthorityUpdate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SalesOrderAuthorityMapper extends BaseMapperX<SalesOrderDO> {
    SalesOrderDO selectBySourceForUpdate(AuthoritySourceQuery query);

    int updateOwnerByVersion(SalesOrderAuthorityUpdate update);
}
