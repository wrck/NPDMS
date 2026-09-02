package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command;

import java.time.LocalDateTime;

public record InspectionRuleDisableUpdate(
        Long tenantId,
        Long revisionId,
        Integer expectedVersion,
        Long disabledBy,
        LocalDateTime disabledAt) {
}
