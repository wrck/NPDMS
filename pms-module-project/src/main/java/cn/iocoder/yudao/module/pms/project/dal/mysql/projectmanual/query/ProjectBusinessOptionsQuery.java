package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import lombok.Builder;
import java.util.Set;

/** 业务选择器的当前可见项目，固定排序及100项上限。 */
@Builder
public record ProjectBusinessOptionsQuery(Long tenantId, Set<Long> visibleProjectIds, String keyword) { }
