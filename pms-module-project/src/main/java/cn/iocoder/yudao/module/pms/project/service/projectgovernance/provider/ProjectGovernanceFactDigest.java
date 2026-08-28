package cn.iocoder.yudao.module.pms.project.service.projectgovernance.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class ProjectGovernanceFactDigest {

    private ProjectGovernanceFactDigest() {
    }

    static String digest(List<String> facts) {
        try {
            String canonical = String.join("\n", facts.stream().sorted().toList());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
