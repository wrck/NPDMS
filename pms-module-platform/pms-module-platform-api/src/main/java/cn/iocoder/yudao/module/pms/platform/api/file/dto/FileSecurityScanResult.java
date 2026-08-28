package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.Set;

public record FileSecurityScanResult(
        String resultCode,
        String providerCode,
        String providerVersion,
        String reasonCode) {

    private static final Set<String> RESULT_CODES = Set.of("PASSED", "REJECTED", "ERROR");

    public FileSecurityScanResult {
        if (resultCode == null || !RESULT_CODES.contains(resultCode)) {
            throw new IllegalArgumentException("invalid file security scan result");
        }
    }
}
