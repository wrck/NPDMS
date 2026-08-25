package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import java.util.Set;

/** 服务端已解析项目范围后的项目分页查询。 */
public record VisibleProjectPageQuery(
        Long tenantId,
        Set<Long> visibleProjectIds,
        PageParam pageParam,
        String projectNameKeyword,
        String projectCodePrefix,
        String status,
        String signingMethod,
        String projectCategory,
        String implementationMode) {
}
