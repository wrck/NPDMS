package cn.iocoder.yudao.module.system.dal.mysql.organization;

import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ManagedOrganizationMapper {
    record TenantQuery(Long tenantId) {}
    record IdQuery(Long tenantId, Long id) {}
    List<CompanyDO> selectCompaniesForUpdate(TenantQuery query);
    List<DeptDO> selectDepartmentsForUpdate(TenantQuery query);
    List<CompanyDO> selectAllCompaniesForUpdate(TenantQuery query);
    List<DeptDO> selectAllDepartmentsForUpdate(TenantQuery query);
    CompanyDO selectCompanyForUpdate(IdQuery query);
    DeptDO selectDepartmentForUpdate(IdQuery query);
    long countClearReferences(TenantQuery query);
    int deleteCompanies(TenantQuery query);
    int deleteDepartments(TenantQuery query);
    int deleteOwnerships(TenantQuery query);
}
