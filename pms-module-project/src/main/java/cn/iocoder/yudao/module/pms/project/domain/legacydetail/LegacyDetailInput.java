package cn.iocoder.yudao.module.pms.project.domain.legacydetail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LegacyDetailInput(
        @NotBlank @Size(max=128) String batchKey,
        @NotBlank @Pattern(regexp="[1-9][0-9]*") @Size(max=64) String sourceProjectKey,
        @NotBlank @Size(max=128) String sourceContractNo,
        @NotNull LocalDateTime sourceReadAt,
        @Pattern(regexp="[a-f0-9]{64}") String expectedSnapshotChecksum,
        @NotNull Map<String, Integer> domainCounts,
        @NotNull @Size(max=10000) List<@Valid SourceRecord> records) {
    public record SourceRecord(
            @NotBlank String domain, @NotBlank @Size(max=191) String sourceKey,
            String parentDomain, @Size(max=191) String parentSourceKey,
            @Size(max=64) String sourceUpdatedAt,
            @NotNull Map<String,String> values) {}
}
