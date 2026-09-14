package cn.iocoder.yudao.module.system.dal.mysql.organization;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.organization.OrganizationOwnershipDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface OrganizationOwnershipMapper extends BaseMapperX<OrganizationOwnershipDO> {
    record Query(Long tenantId,String objectType,Long targetId) {}
    OrganizationOwnershipDO selectForUpdate(Query query);
    default List<OrganizationOwnershipDO> selectTenant(ManagedOrganizationMapper.TenantQuery query) {
        return selectList(new LambdaQueryWrapperX<OrganizationOwnershipDO>().eq(OrganizationOwnershipDO::getTenantId,query.tenantId()));
    }
}

