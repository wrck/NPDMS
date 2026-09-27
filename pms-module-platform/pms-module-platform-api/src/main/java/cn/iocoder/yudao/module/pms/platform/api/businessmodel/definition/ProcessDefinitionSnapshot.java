package cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;

import java.util.List;

/**
 * 已发布中性过程定义快照：稳定编码与冻结版本，实体/操作版本在发布时冻结。
 * 条件是类型化字段筛选，不携带任何引擎表达式或旧运行时对象。
 */
public record ProcessDefinitionSnapshot(
        String definitionCode,
        int definitionVersion,
        String name,
        String ownerModule,
        String entityType,
        String entityStableCode,
        int entityContractVersion,
        String operationCode,
        int operationVersion,
        String ruleCode,
        String ruleVersion,
        List<BusinessFieldFilter> conditions,
        String resultType) {
}
