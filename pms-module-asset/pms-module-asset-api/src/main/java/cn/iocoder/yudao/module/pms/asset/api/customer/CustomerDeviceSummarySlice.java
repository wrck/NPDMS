package cn.iocoder.yudao.module.pms.asset.api.customer;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerDeviceSummarySlice(
        String provider,
        boolean available,
        LocalDateTime dataAsOf,
        List<CustomerDeviceSummaryItem> items,
        long total) {
}
