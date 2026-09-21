package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchivePageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceSelectionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectDeviceSelectionQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceSelectionContextApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_SELECTION_INVALID;

@Service
@RequiredArgsConstructor
public class ProjectDeviceSelectionService implements ProjectDeviceSelectionApi {
    private final ProjectDeviceSelectionMapper mapper;
    private final ProjectDeviceSelectionContextApi contextApi;

    private ProjectDeviceSelectionQuery query(Long projectId) {
        var query = new ProjectDeviceSelectionQuery();
        query.setTenantId(TenantContextHolder.getRequiredTenantId());
        query.setProjectId(projectId);
        query.setContractNumbers(contextApi.getContractNumbers(projectId));
        return query;
    }

    public PageResult<DeviceDO> getPage(DeviceArchivePageReqVO request) {
        var query = query(request.getSelectionProjectId());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setSn(request.getSn());
        query.setName(request.getName());
        query.setProductModel(request.getProductModel());
        query.setContractNo(request.getContractNo());
        return mapper.selectSelectionPage(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SelectedProjectDevice> validateSelection(Long projectId, List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(deviceIds).size() != deviceIds.size()) throw exception(AST_DEVICE_SELECTION_INVALID);
        var query = query(projectId);
        if (deviceIds.isEmpty()) return List.of();
        query.setDeviceIds(deviceIds);
        var devices = mapper.selectSelectionForUpdate(query);
        if (devices.size() != deviceIds.size()) throw exception(AST_DEVICE_SELECTION_INVALID);
        return devices.stream().map(d -> new SelectedProjectDevice(d.getId(), d.getSn(), d.getName(),
                d.getProductCode(), d.getProductModel(), d.getContractNo())).toList();
    }
}
