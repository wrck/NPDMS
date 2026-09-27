package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 迁移批次分页筛选：迁移工具台按归属上下文/用途/来源系统/状态过滤。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MigrationBatchPageQuery extends PageParam {

    private Long tenantId;

    private String ownerContextCode;

    private String purposeCode;

    private String sourceSystem;

    private String batchStatus;
}
