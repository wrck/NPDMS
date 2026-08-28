package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceCustomerRelationshipPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceProjectHistoryPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;

@Service
public class DeviceRelationshipQueryService {

    private static final long DEFAULT_PAGE_NO = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 200L;

    private final DeviceMapper deviceMapper;
    private final DeviceAssignmentMapper assignmentMapper;

    public DeviceRelationshipQueryService(DeviceMapper deviceMapper, DeviceAssignmentMapper assignmentMapper) {
        this.deviceMapper = deviceMapper;
        this.assignmentMapper = assignmentMapper;
    }

    public PageResult<DeviceProjectRelationshipDO> getProjectHistory(
            Long tenantId, Long deviceId, Long pageNo, Long pageSize) {
        DeviceDO device = getDevice(tenantId, deviceId);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        return assignmentMapper.selectProjectHistoryPage(new DeviceProjectHistoryPageQuery(
                tenantId, device.getSn(), (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize));
    }

    public PageResult<DeviceCustomerRelationshipDO> getCustomerRelationships(
            Long tenantId, Long deviceId, Long pageNo, Long pageSize) {
        DeviceDO device = getDevice(tenantId, deviceId);
        long normalizedPageNo = normalizePageNo(pageNo);
        long normalizedPageSize = normalizePageSize(pageSize);
        return assignmentMapper.selectCustomerRelationshipPage(new DeviceCustomerRelationshipPageQuery(
                tenantId, device.getSn(), (normalizedPageNo - 1) * normalizedPageSize, normalizedPageSize));
    }

    private DeviceDO getDevice(Long tenantId, Long deviceId) {
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            var loginUser = SecurityFrameworkUtils.getLoginUser();
            currentTenantId = loginUser == null ? null : loginUser.getTenantId();
        }
        if (currentTenantId == null || !currentTenantId.equals(tenantId)) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        DeviceDO device = deviceMapper.selectByTenantAndId(currentTenantId, deviceId);
        if (device == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        return device;
    }

    private long normalizePageNo(Long pageNo) {
        long normalized = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        if (normalized < 1) {
            throw new IllegalArgumentException("关系分页参数无效");
        }
        return normalized;
    }

    private long normalizePageSize(Long pageSize) {
        long normalized = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (normalized < 1 || normalized > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("关系分页参数无效");
        }
        return normalized;
    }
}
