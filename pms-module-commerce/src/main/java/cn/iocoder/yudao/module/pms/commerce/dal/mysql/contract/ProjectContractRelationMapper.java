package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractIdentityLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractRelationListQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectContractRelationMapper extends BaseMapperX<ProjectContractRelationDO> {
    ProjectContractRelationDO selectByIdentityForUpdate(
            @Param("query") ProjectContractIdentityLockQuery query);

    java.util.List<ProjectContractRelationDO> selectCurrentByContract(
            @Param("query") ContractRelationListQuery query);
}
