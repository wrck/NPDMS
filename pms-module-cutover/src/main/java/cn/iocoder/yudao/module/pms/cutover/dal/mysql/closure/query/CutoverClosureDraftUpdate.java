package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query;

import java.time.LocalDateTime;

public record CutoverClosureDraftUpdate(Long tenantId, Long closureId, Integer expectedVersion,
                                        Boolean preCheckNormal, String preCheckDetail,
                                        Boolean executionNormal, String executionDetail,
                                        Boolean testNormal, String testDetail,
                                        Boolean rollbackOccurred, Boolean rollbackSuccessful,
                                        String rollbackReason, String legacyItems,
                                        String updater, LocalDateTime updateTime) {
}
