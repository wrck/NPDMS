package cn.iocoder.yudao.module.system.service.organization;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanySaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedOrganizationGuard {
    private final ManagedOrganizationMapper mapper;
    private final cn.iocoder.yudao.module.system.dal.mysql.organization.OrganizationOwnershipMapper ownerships;
    public void companyUpdate(CompanySaveReqVO r) {
        // Manual edits are allowed; serialize them with synchronization writes.
        mapper.selectCompanyForUpdate(query(r.getId()));
    }
    public void departmentUpdate(DeptSaveReqVO r) {
        mapper.selectDepartmentForUpdate(query(r.getId()));
    }
    public void departmentDelete(Long id) {
        var old=mapper.selectDepartmentForUpdate(query(id));
        if(old!=null&&managed("DEPARTMENT",id)) throw new cn.iocoder.yudao.framework.common.exception.ServiceException(400,"外部受管部门不可删除");
    }
    private boolean managed(String type,Long id) {
        return ownerships.selectForUpdate(new cn.iocoder.yudao.module.system.dal.mysql.organization.OrganizationOwnershipMapper.Query(
                TenantContextHolder.getRequiredTenantId(),type,id))!=null;
    }
    private ManagedOrganizationMapper.IdQuery query(Long id) {
        return new ManagedOrganizationMapper.IdQuery(TenantContextHolder.getRequiredTenantId(),id);
    }
}
