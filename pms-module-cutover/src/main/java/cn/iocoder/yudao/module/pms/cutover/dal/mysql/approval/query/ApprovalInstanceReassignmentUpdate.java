package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ApprovalInstanceReassignmentUpdate(Long tenantId, Long approvalInstanceId,
                                                   Integer expectedVersion, String holdReasonCode,
                                                   String updater, LocalDateTime updateTime) { }
