package cn.iocoder.yudao.module.pms.project.api.reference;

import java.util.Set;

/** 校验当前登录人对项目的访问权，返回项目有效关联合同号。 */
public interface ProjectDeviceSelectionContextApi {
    Set<String> getContractNumbers(Long projectId);
}
