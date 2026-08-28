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

@Service
@RequiredArgsConstructor
public class DeviceDetailService {

    private final DeviceQueryApi deviceQueryApi;
    private final DeviceNetworkVersionMapper networkVersionMapper;

    public DeviceDetailRespVO getDetail(Long deviceId) {
        DeviceSummaryDTO summary = deviceQueryApi.getDevice(deviceId);
        if (summary == null) {
            return null;
        }
        return new DeviceDetailRespVO(
                summary,
                DeviceSourceSliceRespVO.notAvailable("MES"),
                DeviceSourceSliceRespVO.notAvailable("KNO"),
                networkVersion(summary),
                DeviceSourceSliceRespVO.notAvailable("KNO"),
                DeviceSourceSliceRespVO.notAvailable("AST"),
                DeviceSourceSliceRespVO.notAvailable("AST"));
    }

    private DeviceSourceSliceRespVO networkVersion(DeviceSummaryDTO summary) {
        DeviceNetworkVersionDO version = networkVersionMapper.selectByTenantAndSn(summary.tenantId(), summary.sn());
        if (version == null) {
            return DeviceSourceSliceRespVO.notAvailable("ITR");
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
