package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractIdentityLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectContractRelationMapper extends BaseMapperX<ProjectContractRelationDO> {
    ProjectContractRelationDO selectByIdentityForUpdate(
            @Param("query") ProjectContractIdentityLockQuery query);
}
