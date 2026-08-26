package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query.CustomerDeviceSummaryPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssetCustomerDeviceSummaryApiImpl implements AssetCustomerDeviceSummaryApi {

    private final EquipmentMapper equipmentMapper;

    @Override
    public CustomerDeviceSummarySlice query(CustomerDeviceSummaryQuery query) {
        var page = equipmentMapper.selectCustomerSummaryPage(new CustomerDeviceSummaryPageQuery(
                query.tenantId(), query.customerId(), query.pageNo(), query.pageSize()));
        var items = page.getList().stream()
                .map(equipment -> new CustomerDeviceSummaryItem(equipment.getId(), equipment.getSerialNumber(),
                        equipment.getName(), String.valueOf(equipment.getStatus())))
                .toList();
        return new CustomerDeviceSummarySlice("AST", true, LocalDateTime.now(), items, page.getTotal());
    }
}
