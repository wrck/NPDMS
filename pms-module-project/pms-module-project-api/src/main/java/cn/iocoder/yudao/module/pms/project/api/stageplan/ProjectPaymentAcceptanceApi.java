package cn.iocoder.yudao.module.pms.project.api.stageplan;

import java.time.LocalDateTime;
import java.util.List;

/** Refresh current planned acceptance inputs; never writes execution status or approved schedules. */
public interface ProjectPaymentAcceptanceApi {
    record Entry(String sourceKey, String contractNo, String referenceEvent, String nodeType,
                 String nodeCode, LocalDateTime acceptanceTime, Long boundTargetId) {}
    record Request(Long tenantId, String sourceOwner, List<Entry> entries, boolean adoptExisting) {}
    record Result(String sourceKey, Long targetId, String action, LocalDateTime before,
                  LocalDateTime after, String message) {}
    List<Result> preview(Request request);
    List<Result> refresh(Request request);
}
