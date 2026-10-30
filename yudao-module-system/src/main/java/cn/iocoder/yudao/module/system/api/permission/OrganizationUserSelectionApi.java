package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;

/** 当前业务组织限定候选：在原公司、部门有效授权内匹配姓名、账号或邮箱，不返回邮箱。 */
public interface OrganizationUserSelectionApi {

    PageResult<OrganizationUserCandidateRespDTO> page(OrganizationUserCandidatePageReqDTO request);

}
