package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 四属性受控写入口的乐观锁更新参数。 */
public record ProjectBusinessAttributeUpdate(
        Long tenantId,
        Long projectId,
        Integer expectedVersion,
        String signingMethod,
        String projectCategory,
        String implementationMode,
        String majorProjectLevel) {
}
