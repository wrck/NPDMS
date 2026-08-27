package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.CustomerDeviceSummaryPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssetCustomerDeviceSummaryApiImpl implements AssetCustomerDeviceSummaryApi {

    private final DeviceMapper deviceMapper;

    @Override
    public CustomerDeviceSummarySlice query(CustomerDeviceSummaryQuery query) {
        var page = deviceMapper.selectCustomerSummaryPage(new CustomerDeviceSummaryPageQuery(
                query.tenantId(), query.customerId(), query.pageNo(), query.pageSize()));
        var items = page.getList().stream()
                .map(device -> new CustomerDeviceSummaryItem(device.getId(), device.getSn(),
                        device.getName(), device.getStatus()))
                .toList();
        return new CustomerDeviceSummarySlice("AST", true, LocalDateTime.now(), items, page.getTotal());
    }
}
