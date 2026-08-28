package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

public record CustomerPageCriteria(
        Long tenantId,
        String code,
        String name,
        String departmentCode,
        String marketCode,
        String systemCode,
        String expendCode,
        String industryCode,
        String lifecycleStatus,
        String sourceType,
        PageParam pageParam) {
}
