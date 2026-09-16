package cn.iocoder.yudao.module.pms.integration.extsystem.model.oa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OA OAuth2 token response.
 */
@Data
public class OaTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;
}
