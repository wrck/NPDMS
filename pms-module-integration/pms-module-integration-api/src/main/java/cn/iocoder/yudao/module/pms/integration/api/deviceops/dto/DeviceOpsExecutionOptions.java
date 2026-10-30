package cn.iocoder.yudao.module.pms.integration.api.deviceops.dto;

/** Purpose-only extension; framing matches DAC CollectionExecutionOptions. */
public record DeviceOpsExecutionOptions(String purpose) {
    public DeviceOpsExecutionOptions {
        if (!"PRECHECK".equals(purpose) && !"INSPECTION".equals(purpose)) throw new IllegalArgumentException("invalid execution purpose");
    }
    public String authorizationBinding() {
        String version = "collection-purpose-v1";
        return version.length() + ":" + version + purpose.length() + ":" + purpose;
    }
}
