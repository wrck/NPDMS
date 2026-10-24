package cn.iocoder.yudao.module.pms.asset.api.device.dto;
public record ProjectDeviceInstallationFact(Long deviceId, String sn, String locationSnapshot, Long projectAssignmentVersion) {
    public ProjectDeviceInstallationFact(Long deviceId, String sn, String locationSnapshot) {
        this(deviceId, sn, locationSnapshot, null);
    }
}
