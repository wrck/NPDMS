package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 按项目与一个受控精确目标查询当前执行契约。 */
public record ProjectWorkBindingFactLookupQuery(
        Long tenantId,
        Long projectId,
        String workBindingTypeCode,
        String targetContextCode,
        String targetObjectType,
        String targetObjectKey) {
}
