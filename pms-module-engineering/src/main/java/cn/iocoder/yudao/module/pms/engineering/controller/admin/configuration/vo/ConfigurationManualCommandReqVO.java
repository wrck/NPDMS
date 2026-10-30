package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo;
import lombok.Getter;
import lombok.Setter;
/** Never log this request: includes transient credentials and command text. */
@Getter @Setter
public class ConfigurationManualCommandReqVO {
    private String requestKey;
    private Integer expectedVersion;
    private String host;
    private Integer port;
    private String protocol;
    private String username;
    private char[] password;
    private String commands;
}
