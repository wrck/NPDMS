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

    @Override
    public CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query) {
        long count = projectMapper.selectCountByCustomer(
                new CustomerProjectReferenceQuery(query.tenantId(), query.customerId()));
        String status = count == 0
                ? CustomerReferenceGuardStatus.CLEAR.name()
                : CustomerReferenceGuardStatus.REFERENCED.name();
        return new CustomerReferenceGuardResult(status, "PROJ", count, LocalDateTime.now());
    }
}
