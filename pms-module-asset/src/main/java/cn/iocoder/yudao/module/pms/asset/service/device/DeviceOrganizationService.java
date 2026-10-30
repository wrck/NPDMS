package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceOrganizationRespVO;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 读取已授权设备的固化归属；重建时才访问归属 Owner。 */
@Service
@RequiredArgsConstructor
public class DeviceOrganizationService {
    public record Device(Long id, Long projectId, String contractNo) {}
    private final ProjectDeviceOrganizationApi projects;
    private final ContractDeviceVisibilityApi contracts;
    private final cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper mapper;

    public Map<Long,DeviceOrganizationRespVO> resolve(Long tenantId, List<Device> devices) {
        if (tenantId == null || devices.isEmpty()) return Map.of();
        var ids = devices.stream().map(Device::id).collect(Collectors.toSet());
        var query = new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationBatchQuery(
                tenantId, 0, ids.size(), false, ids, Set.of(), Set.of());
        return mapper.selectStoredOrganizations(query).stream().collect(Collectors.toMap(
                cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO::getId,
                row -> new DeviceOrganizationRespVO(row.getCompanyId(),row.getCompanyName(),row.getDepartmentId(),
                        row.getDepartmentCode(),row.getDepartmentName(),row.getOrganizationSource())));
    }

    public Map<Long,DeviceOrganizationRespVO> resolveSource(Long tenantId, List<Device> devices) {
        if (tenantId == null || devices.isEmpty()) return Map.of();
        Set<Long> projectIds=devices.stream().map(Device::projectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> contractNos=devices.stream().filter(d->d.projectId()==null).map(Device::contractNo)
                .filter(n->n!=null && !n.isBlank()).collect(Collectors.toSet());
        var projectFacts=projectFacts(tenantId,projectIds).stream()
                .collect(Collectors.toMap(ProjectDeviceOrganizationApi.Organization::projectId,Function.identity()));
        var contractFacts=contractFacts(tenantId,contractNos).stream()
                .collect(Collectors.toMap(ContractDeviceVisibilityApi.Organization::contractNo,Function.identity()));
        Map<Long,DeviceOrganizationRespVO> result=new HashMap<>();
        for(var device:devices) {
            if(device.projectId()!=null) {
                var fact=projectFacts.get(device.projectId());
                result.put(device.id(),fact==null ? unresolved("PROJECT") : new DeviceOrganizationRespVO(
                        fact.companyId(),fact.companyName(),fact.departmentId(),fact.departmentCode(),fact.departmentName(),"PROJECT"));
            } else {
                var fact=contractFacts.get(device.contractNo());
                result.put(device.id(),fact==null ? unresolved("UNRESOLVED") : new DeviceOrganizationRespVO(
                        fact.companyId(),fact.companyName(),fact.departmentId(),fact.departmentCode(),fact.departmentName(),"CONTRACT"));
            }
        }
        return result;
    }
    private List<ProjectDeviceOrganizationApi.Organization> projectFacts(Long tenantId,Set<Long> ids) {
        if(ids.isEmpty()) return List.of();
        return projects.getOrganizations(tenantId,ids);
    }
    private List<ContractDeviceVisibilityApi.Organization> contractFacts(Long tenantId,Set<String> numbers) {
        if(numbers.isEmpty()) return List.of();
        return contracts.getOrganizations(tenantId,numbers);
    }
    private DeviceOrganizationRespVO unresolved(String source) {
        return new DeviceOrganizationRespVO(null,null,null,null,null,source);
    }
}
