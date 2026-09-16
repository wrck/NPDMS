package cn.iocoder.yudao.module.pms.integration.extsystem.model.fp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * FP OAuth2 token response.
 */
@Data
public class FpTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;
}
