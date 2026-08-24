package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
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

    private static final Set<Integer> ASSIGNABLE_STATUSES = Set.of(0, 1);

    private final EquipmentMapper equipmentMapper;

    @Override
    public SerialScopeValidationResult validateAssignableSerials(Long tenantId, Long parentProjectId,
                                                                  List<String> serialNumbers) {
        if (tenantId == null || parentProjectId == null || serialNumbers == null) {
            return new SerialScopeValidationResult(false, List.of(), List.of(), List.of());
        }
        Long contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null && !Objects.equals(contextTenantId, tenantId)) {
            return new SerialScopeValidationResult(false, List.of(), List.copyOf(serialNumbers), List.of());
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
        Map<String, EquipmentDO> equipmentBySerial = equipmentMapper.selectListBySerialNumbers(seen).stream()
                .collect(Collectors.toMap(EquipmentDO::getSerialNumber, Function.identity(), (left, right) -> left));
        List<String> missing = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (String serial : seen) {
            EquipmentDO equipment = equipmentBySerial.get(serial);
            if (equipment == null || !Objects.equals(equipment.getTenantId(), tenantId)) {
                missing.add(serial);
            } else if (!ASSIGNABLE_STATUSES.contains(equipment.getStatus())
                    || (equipment.getProjectId() != null && !Objects.equals(equipment.getProjectId(), parentProjectId))) {
                unavailable.add(serial);
            }
        }
        return new SerialScopeValidationResult(missing.isEmpty() && unavailable.isEmpty() && duplicates.isEmpty(),
                List.copyOf(missing), List.copyOf(unavailable), List.copyOf(duplicates));
    }
}
