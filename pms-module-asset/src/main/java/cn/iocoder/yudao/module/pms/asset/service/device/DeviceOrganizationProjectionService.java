package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeviceOrganizationProjectionService implements DeviceOrganizationProjectionApi {
    private final DeviceOrganizationMapper mapper;
    private final DeviceOrganizationService organizations;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refresh(Refresh command) {
        if (command.tenantId() == null) throw new IllegalArgumentException("tenantId required");
        long cursor = 0;
        while (true) {
            var result = rebuild(new DeviceOrganizationBatchQuery(command.tenantId(), cursor, 500, false,
                    command.deviceIds(),command.projectIds(),command.contractNumbers()));
            if (result.processed() == 0) return;
            cursor = result.afterId();
        }
    }

    public record Result(long afterId, int processed, int updated) {}

    @Transactional(rollbackFor = Exception.class)
    public Result rebuildPage(Long tenantId, long afterId) {
        if (tenantId == null || afterId < 0) throw new IllegalArgumentException("Invalid rebuild cursor");
        return rebuild(new DeviceOrganizationBatchQuery(tenantId, afterId, 500, true, Set.of(),Set.of(),Set.of()));
    }

    private Result rebuild(DeviceOrganizationBatchQuery query) {
        var rows = mapper.selectBatchForUpdate(query);
        var facts = organizations.resolveSource(query.tenantId(), rows.stream().map(row ->
                new DeviceOrganizationService.Device(row.getId(),row.getProjectId(),row.getContractNo())).toList());
        // 归属事实相同（全量装载时多为同一 UNRESOLVED 值）的设备合并为一条 IN 更新，避免逐行往返。
        Map<String,DeviceOrganizationUpdate> groups = new LinkedHashMap<>();
        Map<String,List<Long>> groupIds = new LinkedHashMap<>();
        for (var row : rows) {
            var fact = Objects.requireNonNull(facts.get(row.getId()), "Missing organization resolution");
            if (row.getOrganizationUpdatedAt() != null
                    && Objects.equals(row.getCompanyId(),fact.companyId()) && Objects.equals(row.getCompanyName(),fact.companyName())
                    && Objects.equals(row.getDepartmentId(),fact.departmentId()) && Objects.equals(row.getDepartmentCode(),fact.departmentCode())
                    && Objects.equals(row.getDepartmentName(),fact.departmentName()) && Objects.equals(row.getOrganizationSource(),fact.source())) continue;
            var update = new DeviceOrganizationUpdate(query.tenantId(),null,fact.companyId(),fact.companyName(),
                    fact.departmentId(),fact.departmentCode(),fact.departmentName(),fact.source());
            String key = update.companyId()+"|"+update.companyName()+"|"+update.departmentId()+"|"
                    +update.departmentCode()+"|"+update.departmentName()+"|"+update.source();
            groups.putIfAbsent(key,update);
            groupIds.computeIfAbsent(key,ignored->new java.util.ArrayList<>()).add(row.getId());
        }
        int updated = 0;
        for (var entry : groups.entrySet())
            updated += mapper.updateOrganizationBatch(entry.getValue(),groupIds.get(entry.getKey()));
        return new Result(rows.isEmpty() ? query.afterId() : rows.getLast().getId(), rows.size(), updated);
    }
}
