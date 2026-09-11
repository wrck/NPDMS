package cn.iocoder.yudao.module.pms.project.api.customer;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectReferenceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProjectCustomerReferenceGuardApiImpl implements ProjectCustomerReferenceGuardApi {

    private final ProjectMapper projectMapper;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper currentProjects;

    @Override
    public CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query) {
        long count = projectMapper.selectCountByCustomer(
                new CustomerProjectReferenceQuery(query.tenantId(), query.customerId()));
        // 当前项目不能被漏判；旧项目历史的删除保护继续保留。
        count += currentProjects.selectCountCustomerReferences(new CustomerProjectReferenceQuery(query.tenantId(), query.customerId()));
        String status = count == 0
                ? CustomerReferenceGuardStatus.CLEAR.name()
                : CustomerReferenceGuardStatus.REFERENCED.name();
        return new CustomerReferenceGuardResult(status, "PROJ", count, LocalDateTime.now());
    }
}
