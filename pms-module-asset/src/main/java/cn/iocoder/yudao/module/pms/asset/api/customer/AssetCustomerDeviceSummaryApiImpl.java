package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.CustomerDeviceSummaryPageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssetCustomerDeviceSummaryApiImpl implements AssetCustomerDeviceSummaryApi {

    private final DeviceMapper deviceMapper;
    private final DeviceAccessScopeService accessScopeService;

    @Override
    public CustomerDeviceSummarySlice query(CustomerDeviceSummaryQuery query) {
        var visibleProjectIds = accessScopeService.visibleProjectIds(query.tenantId(), query.subjectUserId());
        var page = deviceMapper.selectCustomerSummaryPage(new CustomerDeviceSummaryPageQuery(
                query.tenantId(), query.customerId(), visibleProjectIds, query.pageNo(), query.pageSize()));
        var items = page.getList().stream()
                .map(device -> new CustomerDeviceSummaryItem(device.getId(), device.getSn(),
                        device.getName(), device.getStatus()))
                .toList();
        return new CustomerDeviceSummarySlice("AST", true, LocalDateTime.now(), items, page.getTotal());
    }
}
