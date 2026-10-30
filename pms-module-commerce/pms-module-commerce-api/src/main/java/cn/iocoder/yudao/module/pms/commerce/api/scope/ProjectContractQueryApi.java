package cn.iocoder.yudao.module.pms.commerce.api.scope;

import java.util.Set;

/** 当前租户内项目的有效关联合同号，只读业务事实。 */
public interface ProjectContractQueryApi {
    Set<String> getCurrentContractNumbers(Long projectId);

    /** 当前租户有效关联中合同号逐字精确命中的项目候选；不授予项目读取或办理权限。 */
    Set<Long> findProjectIdsByExactContractNo(ExactContractQuery query);

    record ExactContractQuery(String contractNo) { }
}
