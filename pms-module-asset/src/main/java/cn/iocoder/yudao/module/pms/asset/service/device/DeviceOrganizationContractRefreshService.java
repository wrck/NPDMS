package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceContractOrganizationBatchQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 从既有归属重建流程复制的合同刷新路径：有界分页，同一事务只解析一次 COM 归属。 */
@Service
@RequiredArgsConstructor
public class DeviceOrganizationContractRefreshService {
    private final DeviceOrganizationMapper mapper;
    private final ContractDeviceVisibilityApi contracts;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void refresh(DeviceOrganizationProjectionApi.Refresh command) {
        String afterContract = null;
        long afterId = 0;
        Map<String,ContractDeviceVisibilityApi.Organization> facts = null;
        while (true) {
            var rows = mapper.selectContractBatchForUpdate(new DeviceContractOrganizationBatchQuery(
                    command.tenantId(), command.contractNumbers(), afterContract, afterId, 500));
            if (rows.isEmpty()) return;
            if (facts == null) facts = contracts.getOrganizations(command.tenantId(), command.contractNumbers())
                    .stream().collect(Collectors.toMap(ContractDeviceVisibilityApi.Organization::contractNo, Function.identity()));
            Map<DeviceOrganizationUpdate,List<Long>> groups = new LinkedHashMap<>();
            for (var row : rows) {
                var fact = facts.get(row.getContractNo());
                var update = fact == null
                        ? new DeviceOrganizationUpdate(command.tenantId(), null, null, null, null, null, null, "UNRESOLVED")
                        : new DeviceOrganizationUpdate(command.tenantId(), null, fact.companyId(), fact.companyName(),
                                fact.departmentId(), fact.departmentCode(), fact.departmentName(), "CONTRACT");
                if (row.getOrganizationUpdatedAt() != null
                        && Objects.equals(row.getCompanyId(), update.companyId())
                        && Objects.equals(row.getCompanyName(), update.companyName())
                        && Objects.equals(row.getDepartmentId(), update.departmentId())
                        && Objects.equals(row.getDepartmentCode(), update.departmentCode())
                        && Objects.equals(row.getDepartmentName(), update.departmentName())
                        && Objects.equals(row.getOrganizationSource(), update.source())) continue;
                groups.computeIfAbsent(update, ignored -> new ArrayList<>()).add(row.getId());
            }
            for (var group : groups.entrySet()) {
                if (mapper.updateOrganizationBatch(group.getKey(), group.getValue()) != group.getValue().size())
                    throw new IllegalStateException("设备归属刷新目标变化，当前同步分块已回滚");
            }
            var last = rows.getLast();
            afterContract = last.getContractNo();
            afterId = last.getId();
        }
    }
}
