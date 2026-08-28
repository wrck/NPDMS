package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRecordMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query.DeviceWarrantyRecordPageQuery;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;

@Service
public class DeviceWarrantyQueryService {

    private static final long DEFAULT_PAGE_NO = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 200L;

    private final DeviceMapper deviceMapper;
    private final DeviceWarrantyMapper warrantyMapper;
    private final DeviceWarrantyRecordMapper recordMapper;

    public DeviceWarrantyQueryService(
            DeviceMapper deviceMapper,
            DeviceWarrantyMapper warrantyMapper,
            DeviceWarrantyRecordMapper recordMapper) {
        this.deviceMapper = deviceMapper;
        this.warrantyMapper = warrantyMapper;
        this.recordMapper = recordMapper;
    }

    public DeviceWarrantyResult get(
            Long tenantId,
            Long deviceId,
            Long pageNo,
            Long pageSize) {
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            var loginUser = SecurityFrameworkUtils.getLoginUser();
            currentTenantId = loginUser == null ? null : loginUser.getTenantId();
        }
        if (currentTenantId == null || !currentTenantId.equals(tenantId)) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        long normalizedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        long normalizedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (normalizedPageNo < 1 || normalizedPageSize < 1 || normalizedPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("维保分页参数无效");
        }
        DeviceDO device = deviceMapper.selectByTenantAndId(currentTenantId, deviceId);
        if (device == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        return new DeviceWarrantyResult(
                warrantyMapper.selectByTenantAndDeviceSn(currentTenantId, device.getSn()),
                recordMapper.selectPage(new DeviceWarrantyRecordPageQuery(
                        currentTenantId, device.getSn(), normalizedPageNo, normalizedPageSize)));
    }
}
