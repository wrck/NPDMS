package cn.iocoder.yudao.module.system.service.organization;
import cn.iocoder.yudao.module.system.service.company.CompanyServiceImpl;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanySaveReqVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
/** Reuses upstream validation and writes; serializes manual changes with synchronization. */
@Service @Primary
public class ManagedCompanyService extends CompanyServiceImpl {
    @Resource private ManagedOrganizationGuard guard;
    @Override @Transactional(rollbackFor=Exception.class)
    public void updateCompany(CompanySaveReqVO request) {
        guard.companyUpdate(request);
        super.updateCompany(request);
    }
}
