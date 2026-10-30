package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceDetailRespVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceSourceSliceRespVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionMapper;
import cn.iocoder.yudao.module.pms.asset.domain.version.NetworkSoftwareVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionMapper;
import cn.iocoder.yudao.module.pms.asset.domain.version.FactorySoftwareVersion;
import cn.iocoder.yudao.module.pms.asset.service.warranty.DeviceWarrantyQueryService;

@Service
@RequiredArgsConstructor
public class DeviceDetailService {

    private final DeviceQueryApi deviceQueryApi;
    private final DeviceNetworkVersionMapper networkVersionMapper;
    private final DeviceOrganizationService organizationService;
    private final DeviceFactoryVersionMapper factories;
    private final DeviceWarrantyQueryService warranties;

    public java.util.List<DeviceSummaryDTO> getCurrentSummaries(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery query) {
        return deviceQueryApi.getVisibleDeviceSummaries(query);
    }

    public DeviceDetailRespVO getDetail(Long deviceId) {
        DeviceSummaryDTO summary = deviceQueryApi.getDevice(deviceId);
        if (summary == null) {
            return null;
        }
        return new DeviceDetailRespVO(
                summary,
                factory(summary),
                DeviceSourceSliceRespVO.notAvailable("KNO"),
                networkVersion(summary),
                DeviceSourceSliceRespVO.notAvailable("KNO"),
                warranty(summary),
                DeviceSourceSliceRespVO.notAvailable("AST"),
                organizationService.resolve(summary.tenantId(), java.util.List.of(new DeviceOrganizationService.Device(
                        deviceId,summary.projectId(),summary.contractNo()))).get(deviceId));
    }

    private DeviceSourceSliceRespVO factory(DeviceSummaryDTO summary) {
        var row=factories.selectByTenantAndSn(summary.tenantId(),summary.sn());
        if (row==null) return DeviceSourceSliceRespVO.notAvailable("MES");
        var data=new FactorySoftwareVersion(row.getConpVersion(),row.getConpType(),row.getConpSeries(),row.getConpMark(),
                row.getBootVersion(),row.getCpldVersion(),row.getPcbVersion(),row.getCustomized(),row.getSourceSystem(),
                row.getSourceKey(),row.getSourceVersion(),row.getSourceUpdatedAt(),row.getSyncedAt(),row.getSyncStatus());
        return new DeviceSourceSliceRespVO(row.getSourceSystem(),row.getSourceKey(),row.getSourceVersion(),
                row.getSourceUpdatedAt(),row.getSyncedAt(),row.getSyncStatus(),data);
    }
    private DeviceSourceSliceRespVO warranty(DeviceSummaryDTO summary) {
        var result=warranties.get(summary.tenantId(),summary.deviceId(),1L,1L);
        if (result.current()==null&&result.records().getList().isEmpty()) return DeviceSourceSliceRespVO.notAvailable("AST");
        // The historical record is available without claiming that it is the current effective warranty.
        Object data=result.current()!=null?result.current():result.records().getList().getFirst();
        return new DeviceSourceSliceRespVO("AST",summary.sn(),null,null,null,"SUCCESS",data);
    }
    private DeviceSourceSliceRespVO networkVersion(DeviceSummaryDTO summary) {
        DeviceNetworkVersionDO version = networkVersionMapper.selectByTenantAndSn(summary.tenantId(), summary.sn());
        if (version == null || !"DEVICE_LOG".equals(version.getSourceSystem())) {
            return DeviceSourceSliceRespVO.notAvailable("DEVICE_LOG");
        }
        NetworkSoftwareVersion data = new NetworkSoftwareVersion(
                version.getConpVersion(), version.getConpType(), version.getConpSeries(), version.getConpMark(),
                version.getBootVersion(), version.getCpldVersion(), version.getPcbVersion(), version.getCustomized(),
                version.getSourceSystem(), version.getSourceKey(), version.getSourceVersion(),
                version.getSourceUpdatedAt(), version.getSyncedAt(), version.getSyncStatus(), version.getEffectiveFrom());
        return new DeviceSourceSliceRespVO(
                version.getSourceSystem(), version.getSourceKey(), version.getSourceVersion(),
                version.getSourceUpdatedAt(), version.getSyncedAt(), version.getSyncStatus(), data);
    }
}
