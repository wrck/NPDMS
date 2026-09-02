package cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.query;

import java.time.LocalDateTime;

public final class SpareApplicationQueries {
    private SpareApplicationQueries() {
    }

    public record ById(Long tenantId, Long applicationReferenceId) { }
    public record ByPlatformRequest(Long tenantId, String platformRequestId) { }
    public record ByExternalApplication(Long tenantId, String externalSystemCode, String externalApplicationNo) { }
    public record ByTask(Long tenantId, Long cutoverTaskId) { }
    public record StatusByApplication(Long tenantId, Long applicationReferenceId) { }
    public record StatusByEvent(Long tenantId, String eventId) { }
    public record EvidenceByTask(Long tenantId, Long cutoverTaskId) { }

    public record StoreInitiateResult(Long tenantId, Long applicationReferenceId, Integer expectedVersion,
                                      String expectedStatus, String integrationStatus, String externalRequestId,
                                      String externalApplicationNo, String launchUrl, LocalDateTime lastAttemptAt,
                                      String updater, LocalDateTime updateTime) { }

    public record StoreFailure(Long tenantId, Long applicationReferenceId, Integer expectedVersion,
                               String integrationStatus, Integer retryCount, String failureCode,
                               String failureDetail, LocalDateTime lastAttemptAt,
                               String updater, LocalDateTime updateTime) { }

    public record BindExternalReference(Long tenantId, Long applicationReferenceId, Integer expectedVersion,
                                        String externalRequestId, String externalApplicationNo,
                                        String updater, LocalDateTime updateTime) { }

    public record MoveCurrentStatus(Long tenantId, Long applicationReferenceId, Integer expectedVersion,
                                    Long currentStatusRevisionId, String updater, LocalDateTime updateTime) { }

    public record ClearCurrentStatus(Long tenantId, Long applicationReferenceId, Long expectedRevisionId) { }
}
