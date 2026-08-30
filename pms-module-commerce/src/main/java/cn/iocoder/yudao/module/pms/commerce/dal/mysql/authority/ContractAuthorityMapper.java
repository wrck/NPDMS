package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.ContractAuthorityUpdate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContractAuthorityMapper extends BaseMapperX<ContractDO> {
    ContractDO selectBySourceForUpdate(AuthoritySourceQuery query);

    int updateOwnerByVersion(ContractAuthorityUpdate update);
}
