package cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto;

import cn.iocoder.yudao.module.pms.project.api.deliveryscope.ProjectDeliveryScopeQualificationFactException;

import static cn.iocoder.yudao.module.pms.project.api.deliveryscope.ProjectDeliveryScopeQualificationFactException.Code.INVALID_REQUEST;

/** COM交付范围写命令的当前项目资格读取请求。 */
public record ProjectDeliveryScopeQualificationQuery(
        Long tenantId,
        Long projectId,
        Long actorId) {

    public ProjectDeliveryScopeQualificationQuery {
        if (tenantId == null || tenantId <= 0 || projectId == null || projectId <= 0
                || actorId == null || actorId <= 0) {
            throw new ProjectDeliveryScopeQualificationFactException(INVALID_REQUEST,
                    "tenantId、projectId和actorId必须为正数");
        }
    }
}
