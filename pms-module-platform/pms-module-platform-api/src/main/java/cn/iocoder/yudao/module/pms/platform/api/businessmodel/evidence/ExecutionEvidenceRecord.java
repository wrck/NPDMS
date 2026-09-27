package cn.iocoder.yudao.module.pms.platform.api.businessmodel.evidence;

import java.util.List;

/**
 * 执行证据：项目、计划版本、节点、轮次、规则版本、采纳的事实/结果引用。
 * 与实体身份分离，保留冻结定义、历史完成依据和失效影响；原生执行标识不进入本记录。
 */
public record ExecutionEvidenceRecord(
        String projectStableRef,
        String planVersion,
        String nodeKey,
        long roundNo,
        String ruleVersion,
        List<String> adoptedFactRefs,
        List<String> adoptedResultRefs) {
}
