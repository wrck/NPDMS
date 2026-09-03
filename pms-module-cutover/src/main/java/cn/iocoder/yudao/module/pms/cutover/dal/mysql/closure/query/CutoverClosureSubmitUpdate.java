package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query;

import java.time.LocalDateTime;

public record CutoverClosureSubmitUpdate(Long tenantId, Long closureId, Integer expectedVersion,
                                         String finalResultCode, String resultRef, Long submittedBy,
                                         LocalDateTime submittedAt) {
}
