package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import java.time.LocalDateTime;

/** Ordering is the received evidence time, shared by log and manual current values. */
public final class SoftwareVersionLogPolicy {
    private SoftwareVersionLogPolicy() {}
    public static boolean mayAdopt(DeviceNetworkVersionDO current, LocalDateTime receivedAt) {
        if (receivedAt == null) throw new IllegalArgumentException("Log reception time is required");
        return current == null || current.getEffectiveFrom() == null || !current.getEffectiveFrom().isAfter(receivedAt);
    }
    public static SoftwareVersionValues merge(DeviceNetworkVersionDO current, String conp, String boot, String cpld, String pcb) {
        var prior = SoftwareVersionValues.of(current);
        return new SoftwareVersionValues(conp == null ? prior == null ? null : prior.conpVersion() : conp,
                prior == null ? null : prior.conpType(),
                prior == null ? null : prior.conpSeries(),
                prior == null ? null : prior.conpMark(),
                boot == null ? prior == null ? null : prior.bootVersion() : boot,
                cpld == null ? prior == null ? null : prior.cpldVersion() : cpld,
                pcb == null ? prior == null ? null : prior.pcbVersion() : pcb,
                prior == null ? null : prior.customized(),
                prior == null ? null : prior.releaseDate(),
                prior == null ? null : prior.versionDesc());
    }
}
