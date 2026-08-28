package cn.iocoder.yudao.module.pms.project.api.customer;

import java.time.LocalDateTime;
import java.util.List;

public record CustomerProjectSummarySlice(
        String provider,
        boolean available,
        LocalDateTime dataAsOf,
        List<CustomerProjectSummaryItem> items,
        long total) {
}
