package cn.iocoder.yudao.module.pms.project.api.customer;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.query.CustomerProjectReferenceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProjectCustomerReferenceGuardApiImpl implements ProjectCustomerReferenceGuardApi {

    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper projectMapper;

    @Override
    public CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query) {
        // AI-MIG-000 口径A：引用计数只查新权威主档 proj_project；旧 pms_project 已冻结，
        // V260 已全量前向导入（含旧历史行的客户引用），删除保护由新主档计数完整覆盖。
        long count = projectMapper.selectCountCustomerReferences(
                new CustomerProjectReferenceQuery(query.tenantId(), query.customerId()));
        String status = count == 0
                ? CustomerReferenceGuardStatus.CLEAR.name()
                : CustomerReferenceGuardStatus.REFERENCED.name();
        return new CustomerReferenceGuardResult(status, "PROJ", count, LocalDateTime.now());
    }
}
