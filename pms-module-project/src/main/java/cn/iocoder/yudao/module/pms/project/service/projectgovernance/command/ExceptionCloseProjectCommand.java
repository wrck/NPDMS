package cn.iocoder.yudao.module.pms.project.service.projectgovernance.command;

import java.util.List;

public record ExceptionCloseProjectCommand(
        Long projectId,
        Integer expectedVersion,
        String guardToken,
        String reasonCode,
        String reasonDetail,
        String businessBasis,
        List<LegacyItem> legacyItems,
        String idempotencyKey,
        String requestDigest) {

    public ExceptionCloseProjectCommand {
        legacyItems = legacyItems == null ? null : List.copyOf(legacyItems);
    }

    public record LegacyItem(String type, String summary, String owner, String status) {
    }
}
