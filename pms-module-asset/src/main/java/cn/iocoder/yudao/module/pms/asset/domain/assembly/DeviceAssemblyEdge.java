package cn.iocoder.yudao.module.pms.asset.domain.assembly;

public record DeviceAssemblyEdge(
        String parentDeviceSn,
        String childDeviceSn) {
}
