package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

import java.time.LocalDateTime;

public record EffectiveCredentialGrantQuery(
        Long tenantId,
        Long credentialId,
        String granteeType,
        String granteeId,
        String deviceId,
        String protocol,
        String commandTemplateId,
        LocalDateTime effectiveAt) {
}
