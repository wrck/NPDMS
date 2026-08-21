package cn.iocoder.yudao.module.pms.project.api.acceptance.dto;

import java.util.List;

public record DeliverableInitializationResult(int createdCount, List<Long> deliverableIds) {

    public DeliverableInitializationResult {
        deliverableIds = List.copyOf(deliverableIds);
    }
}
