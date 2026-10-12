package cn.iocoder.yudao.module.pms.asset.dal.dataobject.version;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Retired V4 fields exist only in the all-column withdrawal baseline. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceSoftwareLegacyEventWithdrawalDO extends DeviceNetworkVersionEventDO {
    private String sourceLogKey;
    private Integer sourceDataState;
}
