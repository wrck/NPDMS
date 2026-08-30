package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import java.util.Set;

public record CutoverTaskPageQuery(Long tenantId, Set<Long> visibleProjectIds, Long projectId,
                                   String taskStatus, String currentStage, PageParam pageParam) {
}
