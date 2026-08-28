package cn.iocoder.yudao.module.pms.platform.api.guard;

import java.util.List;

/** 可冻结并在命令提交前逐项重验的提供方事实。 */
public record ProjectGovernanceProviderFact(
        String provider,
        String factVersion,
        String watermark,
        String factDigest,
        List<ProjectGovernanceBlocker> blockers) {

    public ProjectGovernanceProviderFact {
        provider = requireText(provider, "provider");
        factVersion = requireText(factVersion, "factVersion");
        watermark = requireText(watermark, "watermark");
        factDigest = requireText(factDigest, "factDigest");
        if (blockers == null) {
            throw new IllegalArgumentException("blockers must not be null");
        }
        blockers = List.copyOf(blockers);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
