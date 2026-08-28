package cn.iocoder.yudao.module.pms.asset.service.security;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceVisibilityQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class DeviceAccessScopeService {

    private final ProjectScopeApi projectScopeApi;
    private final DeviceMapper deviceMapper;

    public Set<Long> visibleProjectIds(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return Set.of();
        }
        try {
            Set<Long> projectIds = projectScopeApi.resolveAllCurrent(
                    new ProjectAllScopeQuery(tenantId, userId, ProjectScopeApi.ACTION_VIEW));
            return projectIds == null ? Set.of() : Set.copyOf(projectIds);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    public void assertVisible(Long tenantId, Long userId, Long deviceId) {
        Set<Long> projectIds = visibleProjectIds(tenantId, userId);
        if (projectIds.isEmpty() || !deviceMapper.existsVisibleDevice(
                new DeviceVisibilityQuery(tenantId, deviceId, projectIds))) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
    }
}
