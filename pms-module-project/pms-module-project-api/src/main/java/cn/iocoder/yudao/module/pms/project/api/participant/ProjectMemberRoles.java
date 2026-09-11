package cn.iocoder.yudao.module.pms.project.api.participant;

import java.util.Set;

/** 项目成员角色的唯一后端定义；历史别称只参与识别，不生成新的层级角色。 */
public final class ProjectMemberRoles {
    private ProjectMemberRoles() { }

    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String SERVICE_MANAGER = "SERVICE_MANAGER";
    public static final String TEAM_MEMBER = "TEAM_MEMBER";
    public static final String SALES_REPRESENTATIVE = "SALES_REPRESENTATIVE";
    public static final String LEGACY_SERVICE_MANAGER_L1 = "SERVICE_MANAGER_L1";
    public static final String LEGACY_SERVICE_MANAGER_L2 = "SERVICE_MANAGER_L2";
    public static final Set<String> SERVICE_CODES = Set.of(SERVICE_MANAGER,
            LEGACY_SERVICE_MANAGER_L1, LEGACY_SERVICE_MANAGER_L2);
    public static final Set<String> MANAGEMENT_CODES = Set.of(PROJECT_MANAGER, SERVICE_MANAGER,
            LEGACY_SERVICE_MANAGER_L1, LEGACY_SERVICE_MANAGER_L2);
    public static final Set<String> TASK_EXECUTION_CODES = Set.of(PROJECT_MANAGER, SERVICE_MANAGER,
            LEGACY_SERVICE_MANAGER_L1, LEGACY_SERVICE_MANAGER_L2, TEAM_MEMBER);
    public static final Set<String> ORDINARY_CODES = Set.of(TEAM_MEMBER, SALES_REPRESENTATIVE);
    public static final Set<String> ALL_CODES = Set.of(PROJECT_MANAGER, SERVICE_MANAGER,
            LEGACY_SERVICE_MANAGER_L1, LEGACY_SERVICE_MANAGER_L2, TEAM_MEMBER, SALES_REPRESENTATIVE);

    public static boolean isServiceManager(String code) { return code != null && SERVICE_CODES.contains(code); }
    public static String normalize(String code) { return isServiceManager(code) ? SERVICE_MANAGER : code; }
    public static Set<String> storedCodes(String code) {
        return code == null ? Set.of() : isServiceManager(code) ? SERVICE_CODES : Set.of(code);
    }
}
