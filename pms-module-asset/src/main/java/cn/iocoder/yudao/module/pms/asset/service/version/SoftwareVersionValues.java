package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareVersionRecordDO;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Named business values of one registration, independent of mutable current metadata. */
public record SoftwareVersionValues(
        @Size(max=255) String conpVersion,
        @Size(max=100) String conpType,
        @Size(max=100) String conpSeries,
        @Size(max=255) String conpMark,
        @Size(max=255) String bootVersion,
        @Size(max=255) String cpldVersion,
        @Size(max=255) String pcbVersion,
        Boolean customized, LocalDate releaseDate, @Size(max=1000) String versionDesc) {
    public static SoftwareVersionValues of(DeviceNetworkVersionDO row) {
        return row == null ? null : new SoftwareVersionValues(row.getConpVersion(), row.getConpType(),
                row.getConpSeries(), row.getConpMark(), row.getBootVersion(), row.getCpldVersion(),
                row.getPcbVersion(), row.getCustomized(), row.getReleaseDate(), row.getVersionDesc());
    }
    public static SoftwareVersionValues of(DeviceSoftwareVersionRecordDO row) {
        return row == null ? null : new SoftwareVersionValues(row.getConpVersion(), row.getConpType(),
                row.getConpSeries(), row.getConpMark(), row.getBootVersion(), row.getCpldVersion(),
                row.getPcbVersion(), row.getCustomized(), row.getReleaseDate(), row.getVersionDesc());
    }
    public void copyTo(DeviceNetworkVersionDO row) {
        row.setConpVersion(conpVersion); row.setConpType(conpType); row.setConpSeries(conpSeries);
        row.setConpMark(conpMark); row.setBootVersion(bootVersion); row.setCpldVersion(cpldVersion);
        row.setPcbVersion(pcbVersion); row.setCustomized(customized); row.setReleaseDate(releaseDate);
        row.setVersionDesc(versionDesc);
    }
    public void copyTo(DeviceSoftwareVersionRecordDO row) {
        row.setConpVersion(conpVersion); row.setConpType(conpType); row.setConpSeries(conpSeries);
        row.setConpMark(conpMark); row.setBootVersion(bootVersion); row.setCpldVersion(cpldVersion);
        row.setPcbVersion(pcbVersion); row.setCustomized(customized); row.setReleaseDate(releaseDate);
        row.setVersionDesc(versionDesc);
    }
}
