package cn.iocoder.yudao.module.pms.project.api.closure;

import java.util.List;

/** ACC 正常闭环消费的 PROJ 项目上下文与锁契约；锁顺序 root→project 与成员/树/任务写入方一致。 */
public interface ProjectClosureContextApi {

    String PERMISSION_QUERY = "pms:acc-project-closure:query";
    String PERMISSION_SUBMIT = "pms:acc-project-closure:submit";
    String PERMISSION_AUDIT = "pms:acc-project-closure:audit";

    /** 只读或管理级上下文（permission 为 PERMISSION_* 之一；管理级校验项目经理资格）。 */
    ClosureContext read(Long tenantId, Long projectId, Long actorId, String permission);

    /** root 先锁再锁 project；核对项目版本与树版本并重验项目经理。 */
    ClosureContext lock(Long tenantId, Long projectId, Integer expectedProjectVersion,
                        Long expectedTreeVersion, Long actorId);

    /** 锁定项目当前主责服务经理集合（FOR UPDATE），供闭环申请的服务经理核验。 */
    List<Long> lockPrimaryServiceManagerUserIds(Long tenantId, Long projectId);

    record ClosureContext(Long projectId, Long tenantId, Integer projectVersion, String lifecycleStatus,
                          String currentStage, Long managerId, Long rootId, Long treeVersion,
                          String closurePolicySnapshot, Long taskTreeVersion, Long taskProgressVersion,
                          Long lifecycleTemplateId, Integer lifecycleTemplateRevisionNo) {
    }
}
