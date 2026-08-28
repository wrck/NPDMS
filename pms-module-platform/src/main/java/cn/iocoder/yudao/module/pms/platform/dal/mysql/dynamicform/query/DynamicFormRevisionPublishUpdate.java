package cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query;

import java.time.LocalDateTime;

public record DynamicFormRevisionPublishUpdate(
        Long tenantId, Long templateId, Long revisionId, Integer expectedVersion,
        Long publishedBy, LocalDateTime publishedAt, String updater) {
}
