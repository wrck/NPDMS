package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.enums.DeviceArchiveStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetDeviceScopeApiImpl implements AssetDeviceScopeApi {

    private static final Set<String> ASSIGNABLE_STATUSES =
            Set.of(DeviceArchiveStatusEnum.IN_STOCK, DeviceArchiveStatusEnum.IN_USE);

    private final DeviceMapper deviceMapper;

    @Override
    public SerialScopeValidationResult validateAssignableSerials(Long tenantId, Long parentProjectId,
                                                                  List<String> serialNumbers) {
        if (tenantId == null || parentProjectId == null || serialNumbers == null) {
            return new SerialScopeValidationResult(false, List.of(), List.of(), List.of());
        }
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null && !Objects.equals(contextTenantId, tenantId)) {
            return new SerialScopeValidationResult(false, List.copyOf(serialNumbers), List.of(), List.of());
        }
        List<String> normalized = serialNumbers.stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).toList();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        HashSet<String> seen = new HashSet<>();
        normalized.forEach(serial -> {
            if (!seen.add(serial)) {
                duplicates.add(serial);
            }
        });
        Map<String, DeviceDO> deviceBySn = deviceMapper.selectListBySns(seen).stream()
                .collect(Collectors.toMap(DeviceDO::getSn, Function.identity(), (left, right) -> left));
        List<String> missing = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (String serial : seen) {
            DeviceDO device = deviceBySn.get(serial);
            if (device == null || !Objects.equals(device.getTenantId(), tenantId)) {
                missing.add(serial);
            } else if (!ASSIGNABLE_STATUSES.contains(device.getStatus())
                    || (device.getProjectId() != null && !Objects.equals(device.getProjectId(), parentProjectId))) {
                unavailable.add(serial);
            }
        }
        return new SerialScopeValidationResult(missing.isEmpty() && unavailable.isEmpty() && duplicates.isEmpty(),
                List.copyOf(missing), List.copyOf(unavailable), List.copyOf(duplicates));
    }
}
