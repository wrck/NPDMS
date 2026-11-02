package cn.iocoder.yudao.module.pms.commerce.api.scope;

import java.util.Set;
import java.util.List;
import java.time.LocalDateTime;

/** 当前租户内项目的有效关联合同号，只读业务事实。 */
public interface ProjectContractQueryApi {
    Set<String> getCurrentContractNumbers(Long projectId);

    /** Read-only shipment/renewal display, including explicitly retained historical project relations. Not an authorization grant. */
    Set<String> getWarrantyShipmentContractNumbers(Long projectId);
    /** 当前租户有效关联中合同号逐字精确命中的项目候选；不授予项目读取或办理权限。 */
    Set<Long> findProjectIdsByExactContractNo(ExactContractQuery query);

    /** 项目合同投影用的直接关系；包含已失效关系，空项目集合返回空，不授予项目权限。 */
    List<ProjectContractFact> getProjectContractFacts(ProjectContractFactsQuery query);

    record ExactContractQuery(String contractNo) { }
    record ProjectContractFactsQuery(Set<Long> projectIds, LocalDateTime effectiveAt) { }
    record ProjectContractFact(Long projectId, boolean current, String contractNo) { }
}
