package cn.iocoder.yudao.module.pms.asset.domain.version;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.LocalDateTime;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public abstract class SoftwareVersion {

    private final String conpVersion;
    private final String conpType;
    private final String conpSeries;
    private final String conpMark;
    private final String bootVersion;
    private final String cpldVersion;
    private final String pcbVersion;
    private final Boolean customized;
    private final String sourceSystem;
    private final String sourceKey;
    private final String sourceVersion;
    private final LocalDateTime sourceUpdatedAt;
    private final LocalDateTime syncedAt;
    private final String syncStatus;

    protected SoftwareVersion(String conpVersion, String conpType, String conpSeries, String conpMark,
                              String bootVersion, String cpldVersion, String pcbVersion, Boolean customized,
                              String sourceSystem, String sourceKey, String sourceVersion,
                              LocalDateTime sourceUpdatedAt, LocalDateTime syncedAt, String syncStatus) {
        this.conpVersion = conpVersion;
        this.conpType = conpType;
        this.conpSeries = conpSeries;
        this.conpMark = conpMark;
        this.bootVersion = bootVersion;
        this.cpldVersion = cpldVersion;
        this.pcbVersion = pcbVersion;
        this.customized = customized;
        this.sourceSystem = sourceSystem;
        this.sourceKey = sourceKey;
        this.sourceVersion = sourceVersion;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.syncedAt = syncedAt;
        this.syncStatus = syncStatus;
    }

    public String conpVersion() {
        return conpVersion;
    }

    public String conpType() {
        return conpType;
    }

    public String conpSeries() {
        return conpSeries;
    }

    public String conpMark() {
        return conpMark;
    }

    public String bootVersion() {
        return bootVersion;
    }

    public String cpldVersion() {
        return cpldVersion;
    }

    public String pcbVersion() {
        return pcbVersion;
    }

    public Boolean customized() {
        return customized;
    }

    public String sourceSystem() {
        return sourceSystem;
    }

    public String sourceKey() {
        return sourceKey;
    }

    public String sourceVersion() {
        return sourceVersion;
    }

    public LocalDateTime sourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public LocalDateTime syncedAt() {
        return syncedAt;
    }

    public String syncStatus() {
        return syncStatus;
    }
}
