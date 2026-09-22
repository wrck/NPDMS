package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        int updated = 0;
        for (var row : rows) {
            var fact = Objects.requireNonNull(facts.get(row.getId()), "Missing organization resolution");
            if (row.getOrganizationUpdatedAt() == null
                    || !Objects.equals(row.getCompanyId(),fact.companyId()) || !Objects.equals(row.getCompanyName(),fact.companyName())
                    || !Objects.equals(row.getDepartmentId(),fact.departmentId()) || !Objects.equals(row.getDepartmentCode(),fact.departmentCode())
                    || !Objects.equals(row.getDepartmentName(),fact.departmentName()) || !Objects.equals(row.getOrganizationSource(),fact.source())) {
                updated += mapper.updateOrganization(new DeviceOrganizationUpdate(query.tenantId(),row.getId(),fact.companyId(),
                        fact.companyName(),fact.departmentId(),fact.departmentCode(),fact.departmentName(),fact.source()));
            }
        }
        return new Result(rows.isEmpty() ? query.afterId() : rows.getLast().getId(), rows.size(), updated);
    }
}
