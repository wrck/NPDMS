package cn.iocoder.yudao.module.pms.asset.domain.version;

import java.time.LocalDateTime;

public final class TargetSoftwareVersion extends SoftwareVersion {

    public TargetSoftwareVersion(String conpVersion, String conpType, String conpSeries, String conpMark,
                                 String bootVersion, String cpldVersion, String pcbVersion, Boolean customized,
                                 String sourceSystem, String sourceKey, String sourceVersion,
                                 LocalDateTime sourceUpdatedAt, LocalDateTime syncedAt, String syncStatus) {
        super(conpVersion, conpType, conpSeries, conpMark, bootVersion, cpldVersion, pcbVersion, customized,
                sourceSystem, sourceKey, sourceVersion, sourceUpdatedAt, syncedAt, syncStatus);
    }
}
