package cn.iocoder.yudao.module.pms.asset.domain.device;

import java.util.Objects;

public final class DeviceIdentityRules {

    private DeviceIdentityRules() {
    }

    public static String requireSn(String sn) {
        if (sn == null || sn.isBlank()) {
            throw new IllegalArgumentException("设备序列号不能为空");
        }
        return sn.trim();
    }

    public static void requireImmutable(Long currentId, String currentSn, Long requestedId, String requestedSn) {
        if (!Objects.equals(currentId, requestedId) || !Objects.equals(currentSn, requestedSn)) {
            throw new IllegalStateException("设备编号和序列号不可修改");
        }
    }

    public static void requireManualEvidence(String reason, String evidence) {
        if (reason == null || reason.isBlank() || evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("人工补录必须提供原因和证据");
        }
    }
}
