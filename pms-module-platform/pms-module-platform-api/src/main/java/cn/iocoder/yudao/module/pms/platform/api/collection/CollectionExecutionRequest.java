package cn.iocoder.yudao.module.pms.platform.api.collection;

/** Transient user input; never include this object in logs, audit payloads or queues. */
@lombok.Getter @lombok.Setter
public class CollectionExecutionRequest {
    private String requestKey;
    private Integer expectedVersion;
    private Long deviceId;
    private String host;
    private Integer port;
    private String protocol;
    private String username;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private char[] password;
    private String commands;
    private Long templateId;
    private Long credentialId;
    private Long retryOfId;
}
