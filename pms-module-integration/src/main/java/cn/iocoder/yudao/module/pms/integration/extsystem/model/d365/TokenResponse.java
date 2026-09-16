package cn.iocoder.yudao.module.pms.integration.extsystem.model.d365;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * D365 OAuth2 token response.
 */
@Data
public class TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;
}
