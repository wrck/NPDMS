package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record BusinessGrantFileFact(
        String policyKey, String fileSlotKey, Integer fileSequence,
        FileArtifactVersionFact fileFact) {
}
