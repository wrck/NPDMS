package cn.iocoder.yudao.module.pms.project.domain.legacydetail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;

public record LegacyDetailInput(
        @NotBlank @Size(max=128) String batchKey,
        @NotBlank @Pattern(regexp="[1-9][0-9]*") @Size(max=64) String sourceProjectKey,
        @NotBlank @Size(max=128) String sourceContractNo,
        // The evidence adapter sends ISO local time. Override the framework's epoch-only deserializer.
        @NotNull @JsonDeserialize(using=LocalDateTimeDeserializer.class)
        @JsonFormat(shape=JsonFormat.Shape.STRING, lenient=OptBoolean.FALSE) LocalDateTime sourceReadAt,
        @Pattern(regexp="[a-f0-9]{64}") String expectedSnapshotChecksum,
        @NotNull Map<String, Integer> domainCounts,
        @NotNull @Size(max=10000) List<@Valid SourceRecord> records) {
    public record SourceRecord(
            @NotBlank String domain, @NotBlank @Size(max=191) String sourceKey,
            String parentDomain, @Size(max=191) String parentSourceKey,
            @Size(max=64) String sourceUpdatedAt,
            @NotNull Map<String,String> values) {}
}
