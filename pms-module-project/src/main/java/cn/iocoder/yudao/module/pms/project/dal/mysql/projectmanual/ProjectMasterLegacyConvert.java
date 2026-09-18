package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;

/**
 * 项目主档新旧形状转换（AI-MIG-000 / 09-database-design 修订017 / ADR-0022）
 * <p>
 * 旧 {@code pms_project}（ProjectDO）已冻结只读；V260 已将全部存量行前向导入
 * 新权威主档 {@code proj_project}（ProjectMasterDO）。按口径A（V254 有承接领域），
 * 治理/组合/任务/团队/客户守卫的领域服务直接读写新主档；本转换承载领域间的状态
 * 契约映射（治理动作审计的 Integer 粗化状态、组合 STATUS 规则值前向映射）。
 * 无承接旧链过渡消费方（只读链/全景/批量变更/计划变更/阶段/风险）保持旧形状。
 * <p>
 * 字段口径（V260 导入契约）：
 * <ul>
 *   <li>code->projectCode、name->projectName、path->treePath、depth->treeDepth、
 *       sort->treeSort、category->projectCategory、industry->industryName、
 *       contract_code->contractNo、manager_user_id->managerId。</li>
 *   <li>状态粗化映射：旧 Integer 0/1/2/3 -> 新 String S0/S1/S6/S6（2,3 并入 S6）；
 *       逆向按终态值还原 S0->0、S1->1、S6->3，旧 2 的显示伪影已记录。</li>
 *   <li>major_project_flag -> majorProjectLevel 非空等价（V260 存量未知值未写，
 *       导入行为 NULL，存量行重大项目过滤伪影已记录）。</li>
 *   <li>office_id/sales_user_id/shipment_status/source_system/source_business_key/
 *       template_id 未随 V260 导入（组织关系/出货/来源键无解析源），转换后为 NULL。</li>
 * </ul>
 */
public final class ProjectMasterLegacyConvert {

    /** 旧粗化状态终态值（ProjectGovernance 动作审计契约沿用） */
    public static final int LEGACY_STATUS_PENDING_ASSIGN = 0;
    public static final int LEGACY_STATUS_CLOSED = 3;

    /** 新粗化阶段码（V260 导入口径：S0 待指派 / S1 已指派 / S6 关闭态并入） */
    public static final String MASTER_STATUS_PENDING_ASSIGN = "S0";
    public static final String MASTER_STATUS_ACTIVE = "S1";
    public static final String MASTER_STATUS_CLOSED = "S6";

    private ProjectMasterLegacyConvert() {
    }

    /** 新粗化阶段码 -> 旧 Integer（终态还原：S0->0 / S1->1 / S6->3，口径见类注释） */
    public static Integer toLegacyStatus(String masterStatus) {
        if (masterStatus == null) {
            return null;
        }
        return switch (masterStatus) {
            case MASTER_STATUS_PENDING_ASSIGN -> LEGACY_STATUS_PENDING_ASSIGN;
            case MASTER_STATUS_ACTIVE -> 1;
            case MASTER_STATUS_CLOSED -> LEGACY_STATUS_CLOSED;
            default -> null;
        };
    }

    /** 旧 Integer -> 新粗化阶段码（V260 前向口径：0->S0 / 1->S1 / 2,3->S6） */
    public static String toMasterStatus(Integer legacyStatus) {
        if (legacyStatus == null) {
            return null;
        }
        return switch (legacyStatus) {
            case LEGACY_STATUS_PENDING_ASSIGN -> MASTER_STATUS_PENDING_ASSIGN;
            case 1 -> MASTER_STATUS_ACTIVE;
            case 2, LEGACY_STATUS_CLOSED -> MASTER_STATUS_CLOSED;
            default -> null;
        };
    }
}
