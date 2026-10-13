package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRenewalRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceWarrantyRenewalQueryService {
    private final DeviceWarrantyRenewalRecordMapper renewals;
    private final DeviceMapper devices;
    private final DeviceAccessScopeService deviceScope;
    private final ProjectScopeApi projectScope;
    private final ProjectContractQueryApi contracts;

    public record Query(Long tenantId, Long userId, Long projectId, Long deviceId, String sn, int pageNo, int pageSize) {}

    public PageResult<DeviceWarrantyRenewalRecordDO> page(Query query) {
        if (query == null || !Objects.equals(TenantContextHolder.getTenantId(), query.tenantId())
                || query.tenantId() == null || query.userId() == null
                || (query.projectId() == null) == (query.deviceId() == null)
                || query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100
                || query.sn() != null && query.sn().length() > 100) throw new IllegalArgumentException("续保查询范围或分页无效");
        String deviceSn = null;
        Set<String> numbers = Set.of();
        if (query.projectId() != null) {
            var scope = projectScope.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.userId(),
                    query.projectId(), ProjectScopeApi.ACTION_VIEW));
            if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(query.projectId()))
                throw new AccessDeniedException("无权查看该项目续保记录");
            numbers = contracts.getWarrantyShipmentContractNumbers(query.projectId());
            if (numbers.isEmpty()) return new PageResult<>(List.of(), 0L);
        } else {
            deviceScope.assertVisible(query.tenantId(), query.userId(), query.deviceId());
            var device = devices.selectByTenantAndId(query.tenantId(), query.deviceId());
            if (device == null) throw new AccessDeniedException("设备不可见");
            deviceSn = device.getSn();
            if (deviceSn == null || deviceSn.isBlank()) return new PageResult<>(List.of(), 0L);
        }
        var page = new DeviceWarrantyRenewalRecordMapper.PageQuery(query.tenantId(), deviceSn, numbers,
                query.sn(), (long) (query.pageNo() - 1) * query.pageSize(), query.pageSize());
        long total = renewals.count(page);
        return new PageResult<>(total == 0 ? List.of() : renewals.selectPage(page), total);
    }
}
