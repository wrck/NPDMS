package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query.CustomerDeviceReferenceQuery;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssetCustomerReferenceGuardApiImpl implements AssetCustomerReferenceGuardApi {

    private final EquipmentMapper equipmentMapper;

    @Override
    public CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query) {
        long count = equipmentMapper.selectCountByCustomer(
                new CustomerDeviceReferenceQuery(query.tenantId(), query.customerId()));
        String status = count == 0
                ? CustomerReferenceGuardStatus.CLEAR.name()
                : CustomerReferenceGuardStatus.REFERENCED.name();
        return new CustomerReferenceGuardResult(status, "AST", count, LocalDateTime.now());
    }
}
