package cn.iocoder.yudao.module.pms.integration.extsystem.model.d365;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * D365 OAuth2 token request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRequest {

    private String grantType;

    private String clientId;

    private String clientSecret;

    private String scope;
}
