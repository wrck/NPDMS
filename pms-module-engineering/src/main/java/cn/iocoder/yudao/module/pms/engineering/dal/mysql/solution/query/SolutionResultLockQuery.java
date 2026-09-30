package cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.query;

/** 实施方案成果锁读：精确 Owner 身份，主键读也携带租户与项目。 */
public record SolutionResultLockQuery(Long tenantId, Long projectId, Long solutionId) {
    public SolutionResultLockQuery {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || solutionId == null || solutionId <= 0) {
            throw new IllegalArgumentException("实施方案成果范围无效");
        }
    }
}
