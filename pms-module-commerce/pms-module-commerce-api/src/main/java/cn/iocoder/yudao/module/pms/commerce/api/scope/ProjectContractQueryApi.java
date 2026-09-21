package cn.iocoder.yudao.module.pms.commerce.api.scope;

import java.util.Set;

/** 当前租户内项目的有效关联合同号，只读业务事实。 */
public interface ProjectContractQueryApi {
    Set<String> getCurrentContractNumbers(Long projectId);
}
